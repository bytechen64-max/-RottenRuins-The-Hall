package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.SpearOfJudgmentEntity;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

/** 审判之矛 — 蓄力型法术：蓄满后从主人身后 5 格、上方 5 格降下长矛钉向准星落点，无视方块穿透生物；实体逻辑在 {@link SpearOfJudgmentEntity}。 */
public class SpearOfJudgment extends CMagicBaseItem {

    /** 准星落点最大距离（格）：基类默认 32，定点打击给到 48，仍会被属性/附魔折叠。 */
    public static final double BASE_RANGE = 48.0d;

    /** 必须 public（RegisterItem 在别的包用 SpearOfJudgment::new）；末位参数 minChargeRatio=1 即必须蓄满 40 tick 才放。 */
    public SpearOfJudgment() {
        super(new Properties().stacksTo(1), MagicType.DEATH, UsingType.CHARGE,
                100,
                400,
                40,
                5,
                28,
                1);
    }

    @Override
    public double baseTargetRange() {
        return BASE_RANGE;
    }

    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        double range = MagicStats.targetRange(player, stack);
        Vec3 aimPoint = MagicHandle.resolveAimPoint(player, range);

        // 返回 false 只表示没打出实体（落点几乎就在出生点，无可飞距离）；法力与冷却已在框架里扣过。
        return SpearOfJudgmentEntity.spawn(player, stack, aimPoint, castContext.chargeRatio()) != null;
    }
}
