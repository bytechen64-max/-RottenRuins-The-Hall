package org.bytechen.hall.overworld.registry.items.magic.bases;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 *     public boolean cast(ServerPlayer caster, ItemStack stack, MagicCastContext ctx) {
 *         // ctx.target() 就是准星锁定的目标（前提是本法术 needsTarget() 返回 true）
 *         LivingEntity target = ctx.target();
 *         if (target == null) return false;      // 返回 false 表示"这次没打出去"
 *         // ctx.chargeRatio() 就是蓄力完成度，可以拿来缩放威力
 *         return SpellDamageUtil.hurt(target, stack, caster, 12.0f * ctx.chargeRatio());
 *     }
 * }
 * }</pre>
 *
 * <p>需要目标的法术再加两行声明，框架就会在释放那一刻替你解析准星目标：</p>
 * <pre>{@code
 * @Override public boolean needsTarget() { return true; }
 * @Override public boolean allowSelfCast() { return true; }  // 瞄空时打自己（增益类法术）
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
     * {@code KEEP} 模式的<b>触发速度</b>：效果每多少 tick 触发一次。
     *
     * <ul>
     *   <li>{@code 1} = 每 tick 触发（默认，也就是"一直执行"）；</li>
     *   <li>{@code 2} = 每 2 tick 触发一次；</li>
     *   <li>{@code 20} = 每秒触发一次；以此类推。</li>
     * </ul>
     *
     * <p>用它把"表现层每 tick 都在动"和"结算每 N tick 才发生一次"分开：例如一束持续光束
     * 每 tick 都要更新几何，但伤害每 5 tick 结算一次。以前只能自己在 {@code onKeepTick}
     * 里数 tick，现在声明一个数就行。</p>
     *
     * <p><b>触发相位</b>：按下后的<b>第 1 tick 立刻触发一次</b>，之后每 {@code N} tick 一次
     * （不是"先空等 N tick 才第一次触发"）—— 按住右键立刻就有反馈。
     * 等于 {@code 1} 时就是每 tick。</p>
     *
     * <p><b>与耗蓝无关</b>：维持耗蓝（{@link #baseKeepCostPerSecond()}）永远按<b>每秒</b>结算，
     * 不受这个值影响。</p>
     *
     * <p>非 {@code KEEP} 模式忽略这个值。返回值小于 1 会被钳到 1。</p>
     *
     * <p>想在构造器里给就传给 {@code CMagicBaseItem} 的 10 参构造器；
     * 或者直接覆写本方法。</p>
     */
    default int baseKeepTriggerInterval() {
        return 1;
    }

    // ══════════════════════════════════════════════════════════════
    // 设计属性（用覆写声明，不走构造器）
    // ══════════════════════════════════════════════════════════════
    // 下面这几项是"这个法术是什么样"的设计决定，不是可调的数值。做成覆写而不是构造器参数，
    // 是因为 CMagicBaseItem 的构造器已经有 6 个连续的 int —— 再往里塞只会变成
    // "位置参数汤"：把 keepCostPerSecond 和 keepTriggerInterval 填反了照样编译通过，
    // 而且要到实测才发现。覆写写法在子类里一眼就能看到自己声明了什么。

    /**
     * 这个法术是否<b>必须锁定一个目标</b>才能释放。默认 {@code false}。
     *
     * <p>为 {@code true} 时，框架会在<b>真正释放的那一刻</b>沿玩家准星射线解算目标
     * （见 {@code MagicHandle#resolveTarget}），并把结果放进
     * {@link MagicCastContext#target()} 与 {@code MagicCastEvent#getTarget()}。</p>
     *
     * <p>解不到目标时的行为由 {@link #allowSelfCast()} 决定：</p>
     * <ul>
     *   <li>{@code false}（默认）→ 本次释放失败，返回 {@code CastResult.NO_TARGET}，
     *       <b>不扣蓝、不进冷却</b>；</li>
     *   <li>{@code true} → 目标改为施法者自己（"没瞄到人就打自己"）。</li>
     * </ul>
     *
     * <p>蓄力型（{@code CHARGE}）注意：目标是在<b>松手释放的那一刻</b>算的，
     * 不是按下时算的 —— 所以蓄力期间瞄准是有效的。</p>
     */
    default boolean needsTarget() {
        return false;
    }

    /**
     * 没有锁定到目标时，是否允许<b>对自己释放</b>。默认 {@code false}。
     *
     * <p>只有 {@link #needsTarget()} 为 {@code true} 时才有意义。</p>
     *
     * <p>默认 false 是刻意的：把"需要目标"打开的同时默认允许自伤，
     * 会让一个伤害法术在瞄空时反手打死自己 —— 那是很难排查的事故。
     * 自身增益类法术（治疗、护盾、加速）请显式声明 {@code true}。</p>
     */
    default boolean allowSelfCast() {
        return false;
    }

    /**
     * 锁定目标的最大距离（方块）。默认 {@code 32}。
     *
     * <p>射线会被<b>方块阻挡</b>：瞄准墙后的生物不会锁上。实际可用的距离取
     * "本值" 与 "方块命中点" 的较小者。</p>
     */
    default double baseTargetRange() {
        return 32.0d;
    }

    /**
     * {@code KEEP} 模式<b>每次触发效果</b>时扣除的法力。默认 {@code 0}。
     *
     * <p>与 {@link #baseKeepTriggerInterval()} 配合使用，让耗蓝跟着效果走：
     * 触发速度 5 + 每触发 8 点 = 每 5 tick 扣 8 点，也就是平均 32 点/秒。
     * 这样"效果多久发生一次"和"法力多久扣一次"天然是同一个节奏。</p>
     *
     * <p><b>与每秒耗蓝的关系</b>：{@link #baseKeepCostPerSecond()} 是更早的按秒模型，
     * 仍然有效。两者<b>互相独立</b>，同时非 0 就都会扣（不建议，容易算不清）。
     * 新法术请用这个，把 {@code keepCostPerSecond} 留 0。</p>
     *
     * <p>扣费发生在效果触发<b>之后</b>：法力不够时本次触发照常发生，
     * 但维持会在扣费失败时立即结束（不会出现"效果还在、蓝已经透支"）。</p>
     */
    default int baseKeepCostPerTrigger() {
        return 0;
    }

    /**
     * {@code CHARGE} 模式蓄力过程中每 tick 调用（服务端）。
     *
     * @param heldTicks 已经按住了多少 tick
     */
    default void onChargeTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }

    /**
     * {@code KEEP} 模式维持过程中的效果回调（服务端），<b>按触发速度调用</b>。
     *
     * <p>不是每 tick 都调 —— 频率由 {@link #baseKeepTriggerInterval()} 决定
     * （1 = 每 tick，2 = 每 2 tick……）。耗蓝不用在这里扣：框架会按
     * {@link #baseKeepCostPerTrigger()} / {@link #baseKeepCostPerSecond()} 自己结算。</p>
     *
     * <p>这是<b>带锁定目标</b>的版本，框架调用的就是它。默认实现逐级转发到四参、三参版本，
     * 所以<b>只覆写旧版本的代码照常工作</b>；想读到触发速度或目标就覆写这个五参版本。</p>
     *
     * @param heldTicks       从按下到现在多少 tick
     * @param triggerInterval 本次的触发速度（{@code baseKeepTriggerInterval()} 经
     *                        {@code MagicStats} 折叠后的最终值，可能被属性/附魔改过）
     * @param target          本次维持<b>锁定</b>的目标；法术不需要目标时为 {@code null}。
     *                        在开始维持那一刻解析一次并沿用整段维持，期间<b>不会</b>跟着
     *                        准星换人 —— 想做成"光束跟准星走"的话，在每个触发里自己调
     *                        {@code MagicHandle.resolveTarget(caster, stack)} 重新解一个。
     *                        注意它可能已经死了（玩家一直按着不放），用之前看一眼
     *                        {@code isAlive()}。
     * @return {@code false} 表示"主动停止维持"（例如目标死了、玩家移动了）。
     *         返回 {@code false} 后框架会结束这次维持并进冷却。
     */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                               int heldTicks, int triggerInterval,
                               @Nullable LivingEntity target) {
        return onKeepTick(caster, stack, heldTicks, triggerInterval);
    }

    /**
     * {@code KEEP} 模式维持过程中的效果回调 —— <b>四参兼容版本</b>。
     *
     * <p>框架调用的是 {@link #onKeepTick(ServerPlayer, ItemStack, int, int, LivingEntity)}，
     * 它的默认实现会转发到这里。所以覆写本方法拿不到目标，但行为完全不变。</p>
     *
     * <p>新代码建议直接覆写五参版本（需要目标时）。</p>
     */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                               int heldTicks, int triggerInterval) {
        return onKeepTick(caster, stack, heldTicks);
    }

    /**
     * {@code KEEP} 模式维持过程中的效果回调 —— <b>三参兼容版本</b>。
     *
     * <p>转发链的最底层，覆写它会终结整条转发。拿不到触发速度与目标。</p>
     */
    default boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
        return true;
    }

    /** {@code KEEP} 模式结束时的回调（自然结束、法力耗尽、松手都会走到）。 */
    default void onKeepStop(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
    }
}
