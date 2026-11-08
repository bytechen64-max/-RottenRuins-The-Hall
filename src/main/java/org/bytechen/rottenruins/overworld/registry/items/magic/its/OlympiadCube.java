package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.OlympiadCubeEntity;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * <b>奥数立方</b> —— 立刻释放型法术：甩出一颗会自己追目标的小立方体。
 *
 * <h3>三种形态（每颗随机一种）</h3>
 * <table border="1">
 *   <tr><th>形态</th><th>命中效果</th></tr>
 *   <tr><td>三棱锥</td><td>给目标挂 10 秒的「奥数印记」自定义效果；带印记的目标<b>每次受伤</b>都会被
 *       随机塞一个已注册的负面效果（见 {@code ArcaneMarkHandler}）</td></tr>
 *   <tr><td>立方体</td><td>把目标血量直接改成<b>最大生命的 10%</b>
 *       （{@code SetHealthDieUnti.setData}，不钳制，能一路写死）</td></tr>
 *   <tr><td>正十六面体</td><td>接触目标后原地展开成半径 3 格的场，持续 3 秒，
 *       每 tick 一次<b>随机类型</b>的伤害、<b>无视无敌帧</b></td></tr>
 * </table>
 *
 * <h3>普通 vs 特殊使用</h3>
 * <ul>
 *   <li>普通：发 <b>1</b> 颗，形态随机；</li>
 *   <li>特殊使用（同流派法杖 / 同流派饰品）：发 <b>2</b> 颗，且<b>形态互不相同</b>
 *       —— 两颗撞同一个形态等于白送一颗，所以第二颗从"剩下的两种"里抽。</li>
 * </ul>
 *
 * <p>形态本身、飞行、追击、命中结算全在 {@link OlympiadCubeEntity}；
 * 这里只决定"发几颗、各是什么形态、往哪个角度发"。</p>
 */
public class OlympiadCube extends CMagicBaseItem {

    /** 特殊使用时两颗弹的偏转角（度）：左右各偏这么多，避免完全重叠。 */
    public static final float DOUBLE_CAST_SPREAD_DEG = 7.0f;

    protected OlympiadCube(@NotNull Properties properties, @NotNull MagicType magicType,
                           @NotNull UsingType usingType, int manaCost, int cooldownTicks,
                           int chargeTicks, int keepCostPerSecond, float damage) {
        super(properties, magicType, usingType, manaCost, cooldownTicks, chargeTicks,
                keepCostPerSecond, damage);
    }

    public OlympiadCube() {
        this(new Properties().stacksTo(1), MagicType.MYSTERIOUS, UsingType.IMMEDIATELY,
                200,   // 启动耗蓝
                80,    // 冷却 tick（4 秒）
                0,     // 蓄力 tick —— 非 CHARGE
                0,     // 每秒维持耗蓝 —— 非 KEEP
                20.0f);// 伤害基值（立方体形态的改血与展开伤害另有自己的常量）
    }

    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        boolean specialUse = castContext.specialUse();
        int count = specialUse ? 2 : 1;

        int firstForm = player.getRandom().nextInt(OlympiadCubeEntity.FORM_COUNT);
        for (int i = 0; i < count; i++) {
            int form;
            float yawGap;
            if (i == 0) {
                form = firstForm;
                yawGap = specialUse ? -DOUBLE_CAST_SPREAD_DEG : 0.0f;
            } else {
                // 第二颗从"剩下的两种"里抽：两颗同形态等于白送一颗
                int[] rest = Arrays.stream(new int[]{0, 1, 2})
                        .filter(f -> f != firstForm)
                        .toArray();
                form = rest[player.getRandom().nextInt(rest.length)];
                yawGap = DOUBLE_CAST_SPREAD_DEG;
            }
            OlympiadCubeEntity.spawn(player, player.level(), form, yawGap);
        }
        return true;
    }
}
