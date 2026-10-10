package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 法术物品的行为接口。
 *
 * <h3>职责划分（很重要）</h3>
 * <table border="1">
 *   <tr><th>谁</th><th>负责</th></tr>
 *   <tr><td>本接口 / {@link CMagicBaseItem}</td>
 *       <td>只<b>声明基值</b>（耗蓝、冷却、蓄力时长、伤害）与<b>法术效果本身</b>。
 *           不碰冷却与法力的存取。</td></tr>
 *   <tr><td>{@code MagicHandle}</td>
 *       <td>释放流程：查槽位 → 校验冷却 → 校验法力 → 抛事件 → 扣蓝 → 进冷却 → 调 {@link #cast}。
 *           三种释放模式（立刻/蓄力/按住）的时间轴都在这里。</td></tr>
 *   <tr><td>{@code PlayerMagicPool}</td>
 *       <td>槽位与冷却的<b>存储</b>。冷却按「玩家 + 物品注册名」记，所以同名物品共享冷却。</td></tr>
 *   <tr><td>{@code TwistedPoint}</td>
 *       <td>法力的<b>存储</b>。</td></tr>
 *   <tr><td>{@code MagicStats}</td>
 *       <td>把基值交给 {@code MagicStatProvider} 折叠 —— 属性修饰器与附魔挂在这里。</td></tr>
 * </table>
 *
 * <p>这样切分的原因：冷却和法力必须按玩家存（早期版本把它们放在 {@code Item} 单例上，
 * 结果所有玩家共享同一份状态），而法术效果是物品自己的事。混在一起就必然出错。</p>
 *
 * <h3>写一个法术</h3>
 * <pre>{@code
 * public class FireballSpell extends CMagicBaseItem {
 *     public FireballSpell() {
 *         super(new Properties().stacksTo(1),
 *                 MagicType.FIRE, UsingType.CHARGE,
 *                 120,    // 耗蓝
 *                 40,     // 冷却 tick（同名物品共享）
 *                 20,     // 蓄力 tick
 *                 0,      // KEEP 每秒额外耗蓝（非 KEEP 模式无意义）
 *                 12.0f); // 伤害基值
 *     }
 *
 *     @Override
 *     protected boolean cast(ServerPlayer caster, ItemStack stack, MagicCastContext ctx) {
 *         // ctx.chargeRatio() 就是蓄力完成度，可以拿来缩放威力
 *         LivingEntity target = findTarget(caster);
 *         if (target == null) return false;      // 返回 false 表示"这次没打出去"
 *         return SpellDamageUtil.hurt(target, stack, caster, 12.0f * ctx.chargeRatio());
 *     }
 * }
 * }</pre>
 */
public interface IMagicBaseItem {

    // ══════════════════════════════════════════════════════════════
    // 基值声明
    // ══════════════════════════════════════════════════════════════
    // 下面这些是「未被修饰的原始值」。业务代码请通过
    // org.bytechen.hall.api.magic.MagicStats 取，那样才会经过属性/附魔折叠。

    /** 法术流派。 */
    @NotNull
    MagicType getMagicType();

    /** 释放模式：立刻 / 蓄力 / 按住。 */
    @NotNull
    UsingType getUsingType();

    /** 单次释放的启动耗蓝。{@code KEEP} 模式下这是"启动消耗"。 */
    int baseManaCost();

    /** {@code KEEP} 模式维持期间每秒的额外耗蓝；其它模式忽略。 */
    int baseKeepCostPerSecond();

    /** 释放后的冷却 tick。同名物品共享这个冷却。 */
    int baseCooldownTicks();

    /** {@code CHARGE} 模式需要蓄力的 tick 数；其它模式忽略。 */
    int baseChargeTicks();

    /** 伤害基值。实际打多少请用 {@code MagicStats.spellDamage(...)}。 */
    float baseDamage();

    /**
     * 蓄力释放的<b>最低蓄力比例</b>：{@code 0.0~1.0}。
     *
     * <p>这是"提前放效果减弱"和"蓄力不够就作废"之间的分界线：</p>
     * <ul>
     *   <li>松手时蓄力比例 <b>低于</b>它 → 这次释放<b>作废</b>
     *       （{@code CastResult.NOT_CHARGED}，<b>不扣蓝、不进冷却</b>）；</li>
     *   <li><b>不低于</b>它 → 照常放出去，但 {@code MagicCastContext#chargeRatio()}
     *       就是真实完成度，法术自己按它缩放威力（"提前放效果减弱"）。</li>
     * </ul>
     *
     * <p>举几个取值：{@code 0.0} = 点一下就放（只是威力小）；{@code 1.0} = 必须蓄满；
     * 默认 {@value CMagicBaseItem#DEFAULT_MIN_CHARGE_RATIO}（轻点会作废，
     * 但按到四分之一就能放出一个弱化版）。</p>
     *
     * <p>非 {@code CHARGE} 模式忽略这个值。</p>
     */
    default float baseMinChargeRatio() {
        return CMagicBaseItem.DEFAULT_MIN_CHARGE_RATIO;
    }

    // ══════════════════════════════════════════════════════════════
    // 判定与释放
    // ══════════════════════════════════════════════════════════════

    /**
     * 法术自身的额外释放前置条件（冷却与法力由框架检查，不用在这里重复）。
     *
     * <p>例：需要目标、需要在白天、需要玩家不在水中。返回 {@code false} 会让
     * {@code MagicHandle} 以 {@code CastResult.REJECTED} 结束，<b>不扣蓝、不进冷却</b>。</p>
     */
    default boolean canCast(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        return true;
    }

    /**
     * 执行法术效果。
     *
     * <p>调用时机：冷却与法力都已校验并扣除、{@code MagicCastEvent} 未被取消之后。
     * 也就是说走到这里就<b>一定会</b>扣蓝进冷却了，所以这里不要再做"能不能放"的判断，
     * 那种判断属于 {@link #canCast}。</p>
     *
     * @return 是否真的产生了效果。返回 {@code false} 表示"空放"——
     *         框架会照常扣蓝进冷却（因为事件已经放行、玩家也确实按了），
     *         如果你希望空放不进冷却，请在 {@link #canCast} 里挡掉。
     */
    boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack, @NotNull MagicCastContext ctx);

    /**
     * {@code CHARGE} 模式蓄力过程中每 tick 调用（服务端）。
     *
     * @param heldTicks 已经按住了多少 tick
     */
    default void onChargeTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }

    /**
     * {@code KEEP} 模式维持过程中每 tick 调用（服务端）。
     *
     * <p>持续耗蓝由框架按秒结算，不需要自己扣。</p>
     *
     * @return {@code false} 表示"主动停止维持"（例如目标死了、玩家移动了）。
     *         返回 {@code false} 后框架会结束这次维持并进冷却。
     */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
        return true;
    }

    /** {@code KEEP} 模式结束时的回调（自然结束、法力耗尽、松手都会走到）。 */
    default void onKeepStop(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }
}
