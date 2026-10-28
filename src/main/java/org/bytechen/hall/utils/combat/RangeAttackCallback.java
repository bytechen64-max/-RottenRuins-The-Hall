package org.bytechen.hall.utils.combat;

import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * <b>范围攻击命中回调</b> —— 由 {@code RangeAttackUtils.perform(...)} 在结算每个目标时调用，
 * 用来给“范围命中”这件事挂上各生物自己的特殊逻辑。
 *
 * <h3>为什么需要它</h3>
 * <p>范围攻击的“附加表现”五花八门：剑气要沿方向再推一段、某些生物命中后给自己加 buff、
 * 有的要放粒子和音效、有的要额外结算一次真实伤害……如果把这些塞进
 * {@link RangeAttackUtils}，工具类每加一种表现就膨胀一圈，而且每个生物的需求各不相同，
 * 工具类根本不可能预判。</p>
 * <p>反过来，让生物类 {@code implements RangeAttackCallback} 后只覆写自己关心的钩子，
 * 几何判定与伤害结算仍然统一在工具类里 —— 这样“预览范围”和“实际命中”永远是同一份代码，
 * 不会出现“特效扫过去了却没掉血”。</p>
 *
 * <h3>调用顺序（每个目标各一次，全部在服务端同步执行）</h3>
 * <ol>
 *   <li>{@link #beforeHit} —— 返回 {@code false} 表示<b>整个目标跳过</b>：不改伤害、
 *       不击退、不调用 {@link #afterHit}，也不计入
 *       {@code RangeAttackUtils.perform} 的返回值；</li>
 *   <li>{@link #modifyDamage} —— 伤害写入前的最后一处改数值机会
 *       （暴击、护甲穿透、对空加成……）；</li>
 *   <li>工具类执行 {@code hurt}（改完的伤害 ≤ 0 时跳过，但击退与 {@link #afterHit} 照常）；</li>
 *   <li>{@link #afterHit} —— 击退之后调用，<b>无论 hurt 成功与否都会调</b>，
 *       参数里的 {@code hurtSuccess} 就是 {@code LivingEntity#hurt} 的返回值。</li>
 * </ol>
 *
 * <h3>怎么用</h3>
 * <pre>{@code
 * // 1) 生物自己实现：命中即附带 2 秒缓慢
 * public class MyBoss extends Monster implements RangeAttackCallback {
 *     @Override
 *     public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
 *         if (hurtSuccess) {
 *             target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
 *         }
 *     }
 * }
 *
 * // 2) 一次性的匿名类 / lambda 适配器（只关心命中后的表现）
 * RangeAttackUtils.perform(this, RangeAttackUtils.sector().radius(6.0f).damage(10.0f),
 *         RangeAttackCallback.ofAfterHit((target, hurtSuccess) -> spawnSlashParticles(target)));
 * }</pre>
 *
 * <p><b>注意</b>：这些钩子不会在客户端被调用。客户端要做命中表现，
 * 请自己在服务端触发发包，不要指望回调在两端各跑一次 —— 那等于双倍伤害。</p>
 *
 * <p>没有抽象方法，所以可以整体用 {@link #DEFAULT} 占位，也可以只覆写其中一个钩子。</p>
 */
public interface RangeAttackCallback {

    /** 全部钩子都走默认实现的空回调：不干预任何结算，相当于“没有回调”。 */
    RangeAttackCallback DEFAULT = new RangeAttackCallback() {
    };

    /**
     * 命中前的“准入门槛” —— 返回 {@code false} 表示这个目标这一下<b>完全不处理</b>。
     * <p>用途：只对特定状态的目标生效（例如只打“被标记”的目标、跳过正在无敌/骑乘的目标），
     * 或者让目标先做一次豁免判定（闪避、格挡）。</p>
     *
     * @param options 本次范围攻击的定型参数（形状、origin、半径、角度……）
     * @param target  候选目标，已通过几何判定与忽略规则
     * @return {@code true} 继续结算；{@code false} 跳过该目标
     */
    default boolean beforeHit(RangeAttackUtils.Options options, LivingEntity target) {
        return true;
    }

    /**
     * 修改即将写入的伤害数值。
     * <p>在 {@link #beforeHit} 之后、{@code hurt} 之前调用；返回 ≤ 0 表示不造成伤害
     * （但击退与 {@link #afterHit} 仍会执行，方便做“纯推力”技能）。</p>
     *
     * @param options    本次范围攻击的定型参数
     * @param target     目标
     * @param damage     {@code options.getDamage()}，也就是基础伤害
     * @return 实际要写入的伤害
     */
    default float modifyDamage(RangeAttackUtils.Options options, LivingEntity target, float damage) {
        return damage;
    }

    /**
     * 单个目标结算完毕后调用 —— 附加逻辑的主入口（buff、粒子、音效、剑气、额外伤害……）。
     * <p>执行时机：伤害结算与击退<b>之后</b>。因此这里读到的
     * {@code target.getHealth()} / 位置 / 动量都已经是最终状态；
     * 也正因为如此，这里可以放心地再补一次伤害（不会把本次伤害的数值搅乱）。</p>
     *
     * @param options     本次范围攻击的定型参数
     * @param target      目标（可能已经被这一下打死，使用前建议判一下 {@code isAlive()}）
     * @param hurtSuccess {@code LivingEntity#hurt} 是否成功；伤害 ≤ 0 或目标无敌时为 {@code false}
     */
    default void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
    }

    /**
     * 便捷适配器：只关心“命中之后”的表现（粒子、音效、剑气等），其余钩子保持默认。
     * <p>{@link RangeAttackCallback} 有三个 default 方法、没有抽象方法，因此它<b>不是</b>
     * 函数式接口，不能直接写 lambda；这个方法把常用的
     * {@code (target, hurtSuccess) -> ...} 包成一个完整回调。</p>
     *
     * @param action 收到 {@code (目标, hurt 是否成功)}
     * @return 只覆写了 {@link #afterHit} 的回调
     */
    static RangeAttackCallback ofAfterHit(BiConsumer<LivingEntity, Boolean> action) {
        Objects.requireNonNull(action, "action");
        return new RangeAttackCallback() {
            @Override
            public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
                action.accept(target, hurtSuccess);
            }
        };
    }

    /**
     * 便捷适配器：带上下文的版本 —— 需要读 {@code options}（origin / direction / 半径）来做
     * 方向性特效时用它。
     *
     * @param action 收到 {@code (options, 目标, hurt 是否成功)}
     * @return 只覆写了 {@link #afterHit} 的回调
     */
    static RangeAttackCallback ofAfterHit(TriConsumer action) {
        Objects.requireNonNull(action, "action");
        return new RangeAttackCallback() {
            @Override
            public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
                action.accept(options, target, hurtSuccess);
            }
        };
    }

    /**
     * 三参数动作 —— JDK 没有内置的 {@code TriConsumer}，这里补一个，
     * 避免调用方为了拿到 {@code options} 而退回写匿名类。
     */
    @FunctionalInterface
    interface TriConsumer {
        void accept(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess);
    }
}
