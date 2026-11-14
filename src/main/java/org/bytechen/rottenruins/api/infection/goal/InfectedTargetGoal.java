package org.bytechen.rottenruins.api.infection.goal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.bytechen.rottenruins.api.infection.IInfectedEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * 感染生物索敌 AI Goal。
 * <p>
 * 为 {@link IInfectedEntity} 提供开箱即用的索敌逻辑：
 * <ul>
 *   <li>默认追踪最近的非感染生物（包括玩家、动物、村民等）</li>
 *   <li>默认排除所有实现 {@link IInfectedEntity} 的实体（感染生物不攻击感染生物）</li>
 *   <li>通过 {@link Builder} 按需调整范围、视线要求、跨类型攻击等</li>
 *   <li>支持 {@link ITargetFilter} 回调进行自定义白名单/黑名单过滤</li>
 * </ul>
 *
 * <h3>用法示例</h3>
 * <pre>{@code
 * // 最简单的丧尸 — 见到非感染生物就追
 * targetSelector.addGoal(2, InfectedTargetGoal.create(this));
 *
 * // 范围 20 格，不需要视线，且不攻击玩家
 * targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
 *     .range(20.0)
 *     .mustSee(false)
 *     .filter((owner, target) -> !(target instanceof Player))
 *     .build());
 *
 * // 跨模组感染生物互殴（mod_a 和 mod_b 的感染生物攻击彼此）
 * targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
 *     .targetOtherInfected(true)
 *     .build());
 * }</pre>
 *
 * @see IInfectedEntity
 * @see ITargetFilter
 */
public class InfectedTargetGoal extends Goal {

    private final Mob mob;

    private final double range;
    private final double rangeSqr;
    private final double retentionRangeSqr;
    private final boolean mustSee;
    private final boolean targetOtherInfected;

    @Nullable
    private final ITargetFilter filter;

    /** 扫描间隔（tick），避免每 tick 遍历实体列表 */
    private static final int SCAN_INTERVAL = 10;
    /** 丢失目标视线后最多持续追踪的 tick 数（3 秒） */
    private static final int MAX_UNSEEN_TICKS = 60;

    /** 待设定的目标（在 canUse() 中找到，在 start() 中应用） */
    @Nullable
    private LivingEntity pendingTarget;
    @Nullable
    private LivingEntity selectedTarget;
    private int nextScanTick;
    private int lastSeenTick;

    // ==================== 构造器 ====================

    InfectedTargetGoal(Builder builder) {
        this.mob = builder.mob;
        this.range = builder.range;
        this.rangeSqr = builder.range * builder.range;
        // 获取和释放使用不同边界，避免目标在扫描半径边缘反复脱战。
        this.retentionRangeSqr = (builder.range + 4.0) * (builder.range + 4.0);
        this.mustSee = builder.mustSee;
        this.targetOtherInfected = builder.targetOtherInfected;
        this.filter = builder.filter;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    // ==================== 便捷工厂 ====================

    /**
     * 使用默认配置创建实例。
     * <ul>
     *   <li>索敌半径 16 格</li>
     *   <li>需要视线</li>
     *   <li>不攻击任何感染生物</li>
     *   <li>无自定义过滤器</li>
     * </ul>
     *
     * @param mob 索敌主体，建议同时实现 {@link IInfectedEntity} 以获得完整过滤逻辑
     * @return 新实例
     */
    public static InfectedTargetGoal create(Mob mob) {
        return new Builder(mob).build();
    }

    // ==================== Goal 生命周期 ====================

    @Override
    public boolean canUse() {
        pendingTarget = null;
        LivingEntity current = mob.getTarget();
        if (current != null) {
            // 接管还手、召唤物继承等外部目标的生命周期，不能让失效引用永久堵住扫描。
            if (passesPreliminaryCheck(current) && mob.distanceToSqr(current) <= retentionRangeSqr) {
                pendingTarget = current;
                return true;
            }
            mob.setTarget(null);
        }

        if (mob.tickCount < nextScanTick) return false;
        nextScanTick = mob.tickCount + SCAN_INTERVAL;

        pendingTarget = findTarget();
        return pendingTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || target != selectedTarget) return false;
        if (!passesPreliminaryCheck(target)) return false;
        if (mob.distanceToSqr(target) > retentionRangeSqr) return false;

        if (mustSee) {
            if (mob.getSensing().hasLineOfSight(target)) {
                lastSeenTick = mob.tickCount;
            } else if (mob.tickCount - lastSeenTick > MAX_UNSEEN_TICKS) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void start() {
        mob.setTarget(pendingTarget);
        selectedTarget = mob.getTarget() == pendingTarget ? pendingTarget : null;
        lastSeenTick = mob.tickCount;
        pendingTarget = null;
    }

    @Override
    public void stop() {
        if (mob.getTarget() == selectedTarget) mob.setTarget(null);
        selectedTarget = null;
        pendingTarget = null;
        nextScanTick = 0;
    }

    // ==================== 索敌逻辑 ====================

    /**
     * 在范围内寻找最近的有效目标。
     */
    @Nullable
    private LivingEntity findTarget() {
        AABB aabb = mob.getBoundingBox().inflate(range);
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                LivingEntity.class, aabb, this::passesPreliminaryCheck);

        // 找最近的
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity entity : candidates) {
            double dist = mob.distanceToSqr(entity);
            if (dist > rangeSqr || dist >= closestDist) continue;
            if (mustSee && !mob.getSensing().hasLineOfSight(entity)) continue;
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        return closest;
    }

    /**
     * 第一轮快速筛选：排除自身、已死亡、不可攻击的类型。
     * 在 {@code getEntitiesOfClass} 中作为 predicate 使用，减少扫描开销。
     */
    private boolean passesPreliminaryCheck(LivingEntity target) {
        if (target == mob) return false;
        if (!target.isAlive() || target.isRemoved() || target.level() != mob.level()) return false;
        if (!target.isAttackable()) return false;
        if (!mob.canAttack(target) || mob.isAlliedTo(target)) return false;
        if (!mob.canAttackType(target.getType())) return false;

        // 排除创造和旁观模式玩家，与原版 NearestAttackableTargetGoal 行为一致
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
        }

        return passesInfectionFilter(target) && passesCustomFilter(target);
    }

    /**
     * 感染类型过滤。
     * <ul>
     *   <li>目标不是 {@link IInfectedEntity} → 通过（玩家、动物、村民等）</li>
     *   <li>目标是 {@link IInfectedEntity} 且 owner 未实现该接口 → 通过</li>
     *   <li>目标是 {@link IInfectedEntity} 且未开启跨类型 → 拒绝</li>
     *   <li>目标是 {@link IInfectedEntity} 且已开启跨类型、类型不同 → 通过</li>
     *   <li>目标是 {@link IInfectedEntity} 且已开启跨类型、类型相同 → 拒绝</li>
     * </ul>
     */
    private boolean passesInfectionFilter(LivingEntity target) {
        if (!(target instanceof IInfectedEntity infectedTarget)) {
            // 非感染生物（包括玩家）— 默认允许
            return true;
        }
        // Goal 在 Mob 构造期间注册，此时子类字段可能尚未初始化；运行时再读取阵营。
        ResourceLocation ownerType = mob instanceof IInfectedEntity infected ? infected.getInfectionType() : null;
        if (ownerType == null) {
            // 拥有者不标识感染类型 — 不做感染过滤
            return true;
        }
        if (!targetOtherInfected) {
            // 不允许攻击任何感染生物
            return false;
        }
        // 开启跨类型：只攻击不同类型，不攻击同类
        return !ownerType.equals(infectedTarget.getInfectionType());
    }

    /**
     * 自定义过滤器。null = 不附加限制。
     */
    private boolean passesCustomFilter(LivingEntity target) {
        return filter == null || filter.canTarget(mob, target);
    }

    // ==================== Builder ====================

    /**
     * {@link InfectedTargetGoal} 的 Builder。
     * <p>
     * 所有参数均设有合理默认值，只需传入 {@link Mob} 实例即可构建。
     */
    public static class Builder {

        private final Mob mob;

        /** 索敌半径（格），默认 16 */
        private double range = 16.0;
        /** 是否需要目标可见，默认 true */
        private boolean mustSee = true;
        /** 是否允许攻击不同类型感染生物，默认 false */
        private boolean targetOtherInfected = false;
        /** 自定义过滤回调，默认 null */
        @Nullable
        private ITargetFilter filter = null;

        /**
         * @param mob 索敌主体，建议实现 {@link IInfectedEntity} 以获得感染类型感知
         */
        public Builder(Mob mob) {
            this.mob = mob;
        }

        /**
         * 设置索敌半径（格）。
         *
         * @param range 半径，必须为正数
         */
        public Builder range(double range) {
            this.range = Math.max(1.0, range);
            return this;
        }

        /**
         * 设置是否需要目标在视线范围内。
         *
         * @param mustSee true = 需要视线无遮挡
         */
        public Builder mustSee(boolean mustSee) {
            this.mustSee = mustSee;
            return this;
        }

        /**
         * 设置是否允许攻击其他类型的感染生物。
         * <p>
         * 为 {@code false}（默认）时，所有 {@link IInfectedEntity} 都会被排除，
         * 感染生物只会攻击非感染目标。
         * <p>
         * 为 {@code true} 时，允许攻击<b>不同感染类型</b>的感染生物
         * （如 {@code mod_a:zombie} 可以攻击 {@code mod_b:parasite}），
         * 但同类型感染生物之间仍然不会互相攻击。
         *
         * @param targetOtherInfected 是否允许跨类型攻击感染生物
         */
        public Builder targetOtherInfected(boolean targetOtherInfected) {
            this.targetOtherInfected = targetOtherInfected;
            return this;
        }

        /**
         * 设置自定义索敌过滤器。
         * <p>
         * 该回调在默认筛选之后调用——到达时目标已被确认为：
         * 存活的、可攻击的、非自身的、通过感染类型过滤的实体。
         * <p>
         * 传 {@code null} 表示不附加额外限制。
         *
         * @param filter 自定义过滤器，可为 null
         */
        public Builder filter(@Nullable ITargetFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * 构建 {@link InfectedTargetGoal} 实例。
         *
         * @return 新的 Goal 实例
         */
        public InfectedTargetGoal build() {
            return new InfectedTargetGoal(this);
        }
    }
}
