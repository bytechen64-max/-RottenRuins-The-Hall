package org.bytechen.rottenruins.utils.combat;

import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;
import java.util.function.BiConsumer;

/** 范围攻击命中回调：由 {@code RangeAttackUtils.perform(...)} 逐目标调用，让各生物挂自己的附加表现，而几何判定与伤害结算仍统一在工具类里；只在服务端触发，客户端要做命中表现请自行发包，否则双倍。 */
public interface RangeAttackCallback {

    /** 全部钩子走默认实现的空回调：不干预任何结算。 */
    RangeAttackCallback DEFAULT = new RangeAttackCallback() {
    };

    /** 命中前的准入门槛；返回 false 表示整个目标跳过——不改伤害、不击退、不调 afterHit，也不计入 perform 的返回值。 */
    default boolean beforeHit(RangeAttackUtils.Options options, LivingEntity target) {
        return true;
    }

    /** 伤害写入前最后一次改数值的机会；返回 ≤ 0 时仍会击退并调用 afterHit（便于做纯推力技能）。 */
    default float modifyDamage(RangeAttackUtils.Options options, LivingEntity target, float damage) {
        return damage;
    }

    // 在伤害与击退之后调用，无论 hurt 是否成功；此处读到的血量/位置/动量已是最终状态，可安全补伤害。
    default void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
    }

    /** 只覆写 afterHit 的适配器：本接口没有抽象方法、不是函数式接口，不能直接写 lambda。 */
    static RangeAttackCallback ofAfterHit(BiConsumer<LivingEntity, Boolean> action) {
        Objects.requireNonNull(action, "action");
        return new RangeAttackCallback() {
            @Override
            public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
                action.accept(target, hurtSuccess);
            }
        };
    }

    /** 同上，但把 options（origin / direction / 半径）一并交给回调，用于方向性特效。 */
    static RangeAttackCallback ofAfterHit(TriConsumer action) {
        Objects.requireNonNull(action, "action");
        return new RangeAttackCallback() {
            @Override
            public void afterHit(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess) {
                action.accept(options, target, hurtSuccess);
            }
        };
    }

    /** 三参数动作：JDK 没有内置 TriConsumer，补在这里免得调用方为了拿到 options 退回写匿名类。 */
    @FunctionalInterface
    interface TriConsumer {
        void accept(RangeAttackUtils.Options options, LivingEntity target, boolean hurtSuccess);
    }
}
