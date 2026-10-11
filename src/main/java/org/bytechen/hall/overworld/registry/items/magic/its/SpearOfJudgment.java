package org.bytechen.hall.overworld.registry.items.magic.its;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.api.magic.MagicStats;
import org.bytechen.hall.overworld.registry.entities.magic.SpearOfJudgmentEntity;
import org.bytechen.hall.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.hall.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

/**
 * <b>审判之矛</b> —— 蓄力型法术：蓄满后从主人<b>身后 5 格、上方 5 格</b>降下一根长矛，
 * 笔直钉向准星指定的位置，途中<b>无视方块、穿透生物</b>，沿途造成伤害。
 *
 * <p>飞行、穿透与伤害判定都在 {@link SpearOfJudgmentEntity} 里，这里只决定
 * "从哪打到哪"：</p>
 * <ol>
 *   <li>射程 = {@code MagicStats.targetRange(player, stack)} —— 也就是
 *       {@link #baseTargetRange()}（这里覆写成了 48 格）经过属性/附魔折叠后的值；</li>
 *   <li>落点 = {@link MagicHandle#resolveAimPoint}：准星先打生物，没有就打方块，
 *       对着天就是"射程那么远的空中一点"；</li>
 *   <li>伤害倍率 = {@code ctx.chargeRatio()}。</li>
 * </ol>
 *
 * <h3>蓄力语义</h3>
 * <p>构造器里 {@code minChargeRatio = 1}，也就是<b>必须蓄满才放</b>
 * （不到 40 tick 松手 → {@code NOT_CHARGED}，不扣蓝不进冷却）。
 * 把那个值调低就会变成"提前放 = 弱化版"：伤害倍率这条线已经接好了
 * —— {@code cast} 把 {@code ctx.chargeRatio()} 传给 {@code SpearOfJudgmentEntity}，
 * 后者在每次命中时乘上它，不用再改实体。</p>
 */
public class SpearOfJudgment extends CMagicBaseItem {

    /**
     * 准星落点的最大距离（格）。
     * <p>默认 {@code baseTargetRange()} 是 32；审判之矛是"从天而降的定点打击"，
     * 给到 48 格才够打远处的目标。仍然可被属性/附魔折叠。</p>
     */
    public static final double BASE_RANGE = 48.0d;

    /**
     * 注意是 {@code public} —— 法术要被 {@code RegisterItem} 里的
     * {@code ITEMS.register("...", SpearOfJudgment::new)} 引用，那是在<b>另一个包</b>里，
     * {@code protected} 构造器在那里访问不到。
     */
    public SpearOfJudgment() {
        super(new Properties().stacksTo(1), MagicType.DEATH, UsingType.CHARGE,
                100,    // 启动耗蓝
                600,    // 冷却 tick —— 30 秒，大招该有的代价
                40,     // 蓄力 tick —— 要按住 2 秒
                5,      // 每秒维持耗蓝（非 KEEP 模式，基类会归零，填着不影响）
                50,     // 直接伤害基值
                1);     // 最低蓄力比例 1.0 = 必须蓄满
    }

    /** 射程：48 格（会再被属性/附魔折叠）。 */
    @Override
    public double baseTargetRange() {
        return BASE_RANGE;
    }

    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        double range = MagicStats.targetRange(player, stack);
        Vec3 aimPoint = MagicHandle.resolveAimPoint(player, range);

        // 生成失败（落点几乎就在出生点上，没有可飞的距离）时返回 false：
        // 法力与冷却已经在框架里扣过了，这里只是没打出实体。
        return SpearOfJudgmentEntity.spawn(player, stack, aimPoint, castContext.chargeRatio()) != null;
    }
}
