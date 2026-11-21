package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.overworld.registry.entities.population.magic.FireBallEntity;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * <b>火球术</b> —— 立刻释放型法术：朝准星方向打出一颗火球。
 *
 * <p>火球命中生物或方块后爆炸：<b>破坏方块</b>（受 {@code mobGriefing} 影响，
 * 和原版恶魂火球一致）并造成范围伤害；命中生物时另外结算一次直接伤害。
 * 飞行、拖尾、被打偏都在 {@link FireBallEntity} 里（原版 {@code Fireball} 的子类）。</p>
 *
 * <h3>法术强度怎么作用到这颗火球上</h3>
 * <p>"法术强度"取的是 {@code MagicStats.spellPower} ——
 * <b>通用法强（{@code spell_power}，默认 0）加上火系法强
 * （{@code spell_power_fire}，默认 0）</b>。它同时影响：</p>
 * <table border="1">
 *   <tr><th>作用对象</th><th>怎么生效</th></tr>
 *   <tr><td>爆炸半径</td>
 *       <td>{@link #cast} 里现算：{@code 基值 × (1 + 法术强度)}，钳在
 *           {@value #MIN_EXPLOSION_RADIUS} ~ {@value #MAX_EXPLOSION_RADIUS} 格</td></tr>
 *   <tr><td>爆炸伤害</td>
 *       <td>原版爆炸的伤害由半径推出（中心约 {@code 7 × 半径 + 1}），
 *           半径变大伤害就<b>自动</b>变大</td></tr>
 *   <tr><td>直接命中伤害</td>
 *       <td>走 {@code MagicDamageEvent}，被 {@code MagicAttributeHandlers} 乘上同一个属性</td></tr>
 * </table>
 *
 * <p>也就是说"伤害"与"破坏范围"是同一条链上的两个量，<b>没法只要其中一个变大</b>。
 * 想让它们分开，就把 {@code FireBallEntity} 的范围伤害改成自己遍历实体结算
 * —— 那样半径就只管破坏方块了。</p>
 *
 * <h3>想改这个法术</h3>
 * <ul>
 *   <li>更大/更小的火球 → 覆写 {@link #baseExplosionRadius()}（基值，仍会被法术强度乘一遍）；</li>
 *   <li>耗蓝 / 冷却 / 直接伤害 → 改构造器参数（都是基值，能被属性与附魔折叠）。</li>
 * </ul>
 */
public class FireBall extends CMagicBaseItem {

    /** 爆炸半径基值（格）。实际半径 = 它 × 法术强度。 */
    public static final double BASE_EXPLOSION_RADIUS = 2.0d;

    /** 半径下限：法术强度被减到 0 时也不至于变成"没有爆炸"。 */
    public static final double MIN_EXPLOSION_RADIUS = 0.5d;

    /**
     * 半径上限（格）。
     * <p>防呆用：{@code spell_power} 是可以反复叠加的倍率属性，没有上限的话
     * 一条 {@code /attribute} 就能造出半径几百格的火球，把区块炸穿。</p>
     */
    public static final double MAX_EXPLOSION_RADIUS = 12.0d;

    /** 特殊使用附带的两颗小火球：半径是主火球的比例。 */
    public static final double SMALL_RADIUS_RATIO = 0.5d;

    /** 特殊使用时左右两颗的偏转角（度）。 */
    public static final float SPREAD_DEG = 8.0f;

    public FireBall() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.IMMEDIATELY, MagicType.FIRE)
                .manaCost(40)
                .cooldownTicks(40)
                .damage(4.0f)
                .build());
    }

    /**
     * 爆炸半径基值（格）。想做"大型火球"就覆写它（例如 {@code return 5.0d;}），
     * 法术强度仍然会照乘。
     */
    public double baseExplosionRadius() {
        return BASE_EXPLOSION_RADIUS;
    }
    @Override
    public boolean cast(@NotNull ServerPlayer player, @NotNull ItemStack stack,
                        @NotNull MagicCastContext castContext) {
        // 0 法强时 powerScale = 1.0，所以不会因为属性缺失把火球缩成 0 半径。
        double radius = Mth.clamp(baseExplosionRadius() * MagicStats.powerScale(player, stack),
                MIN_EXPLOSION_RADIUS, MAX_EXPLOSION_RADIUS);

        // 火球的出生点、瞄准方向、伤害与爆炸全在实体里；这里只决定"炸多大、炸不炸方块"。
        FireBallEntity.spawn(player, stack, radius, player.isShiftKeyDown());
        if (castContext.specialUse()) {
            // 特殊使用：额外的两颗小火球，分别向左右偏开。
            double small = Mth.clamp(radius * SMALL_RADIUS_RATIO, MIN_EXPLOSION_RADIUS, MAX_EXPLOSION_RADIUS);
            FireBallEntity.spawn(player, stack, small, player.isShiftKeyDown(), -SPREAD_DEG);
            FireBallEntity.spawn(player, stack, small, player.isShiftKeyDown(), SPREAD_DEG);
        }
        return true;
    }
}
