package org.bytechen.rottenruins.utils.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 范围攻击工具类：给定“形状 + 参数”，一次找出范围内的生物，并统一结算伤害、击退与命中回调。
 * <p>几何判定只有一份私有实现（{@code matches}），{@link #contains} 与实际命中走同一段代码，预览与结算天然一致。
 * <p>伤害/击退/回调<b>只在服务端</b>执行：客户端调 {@link #perform} 直接返回 0，不会两端各打一次造成双倍伤害。
 * <p>实体查询按 section 线性扫描、成本跟外包 AABB 体积相关，所以只用“刚好包住形状”的保守盒子粗筛。
 */
public final class RangeAttackUtils {

    /** 角度比较容差：目标卡在扇面边缘时，浮点误差不该决定“这一下算不算打中”。 */
    private static final double ANGLE_EPSILON = 1.0E-6;

    /** 粗筛 AABB 的微小外扩：原版 {@code AABB#intersects} 用严格不等号，零厚度盒子与任何实体都不相交。 */
    private static final double BROAD_PHASE_MARGIN = 1.0E-3;

    /** 判定“向量退化到没有方向”的平方长度阈值。 */
    private static final double DEGENERATE_SQR = 1.0E-8;

    /** 目标正好压在 origin 上时，判断方向水平分量是否可用的阈值。 */
    private static final double KNOCKBACK_MIN_HORIZONTAL = 1.0E-4;

    /** direction 未设置或退化成零向量时的兜底轴向（+Z），保证角度判定永远有确定的轴。 */
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0.0, 0.0, 1.0);

    private RangeAttackUtils() {
    }

    /**
     * 命中范围的几何形状。角度是<b>全角</b>不是半角（{@code angleDegrees = 90} 表示左右各 45°）；
     * SECTOR / ANNULUS 的垂直判定看目标<b>碰撞箱</b>是否与 origin.y ± verticalRange 相交，否则高个生物会被漏判。
     */
    public enum Shape {
        SECTOR,
        CONE,
        SPHERE,
        CYLINDER,
        ANNULUS
    }

    /** 一次范围攻击的<b>不可变</b>参数快照，只能由 {@link Builder#build()} 产生：build 时统一钳制与 direction 归一化。 */
    public static final class Options {

        private final Shape shape;
        private final Vec3 origin;
        private final Vec3 direction;
        private final float radius;
        private final float innerRadius;
        private final float angleDegrees;
        private final float verticalRange;
        private final float damage;
        @Nullable
        private final DamageSource damageSource;
        private final boolean requireLineOfSight;
        private final boolean ignoreAllies;
        private final boolean ignoreCreative;
        private final boolean ignoreInvulnerable;
        private final boolean resetInvulnerableTime;
        private final int maxTargets;
        private final float knockbackStrength;
        private final float knockbackUpward;
        @Nullable
        private final Predicate<LivingEntity> filter;
        @Nullable
        private final RangeAttackCallback callback;

        private Options(Builder builder, Vec3 origin, Vec3 direction) {
            this.shape = builder.shape;
            this.origin = origin;
            this.direction = direction;
            this.radius = Math.max(0.0F, builder.radius);

            // innerRadius >= radius 会让 ANNULUS 退化成“整个扇面”，与“贴脸不吃伤害”的意图相反，所以按贴边处理。
            float inner = Math.max(0.0F, builder.innerRadius);
            if (inner >= this.radius) {
                inner = Math.max(0.0F, this.radius - 1.0E-3F);
            }
            this.innerRadius = inner;

            // 360 是“不限制角度”的哨兵值，所以上限钳到 360；负角度钳到 0。
            this.angleDegrees = Mth.clamp(builder.angleDegrees, 0.0F, 360.0F);
            this.verticalRange = Math.max(0.0F, builder.verticalRange);
            this.damage = builder.damage;
            this.damageSource = builder.damageSource;
            this.requireLineOfSight = builder.requireLineOfSight;
            this.ignoreAllies = builder.ignoreAllies;
            this.ignoreCreative = builder.ignoreCreative;
            this.ignoreInvulnerable = builder.ignoreInvulnerable;
            this.resetInvulnerableTime = builder.resetInvulnerableTime;
            this.maxTargets = Math.max(0, builder.maxTargets);
            this.knockbackStrength = Math.max(0.0F, builder.knockbackStrength);
            this.knockbackUpward = Math.max(0.0F, builder.knockbackUpward);
            this.filter = builder.filter;
            this.callback = builder.callback;
        }

        public Shape getShape() {
            return this.shape;
        }

        public Vec3 getOrigin() {
            return this.origin;
        }

        public Vec3 getDirection() {
            return this.direction;
        }

        public float getRadius() {
            return this.radius;
        }

        public float getInnerRadius() {
            return this.innerRadius;
        }

        public float getAngleDegrees() {
            return this.angleDegrees;
        }

        public float getVerticalRange() {
            return this.verticalRange;
        }

        /** 基础伤害；{@code <= 0} 时不调用 {@code hurt}（但仍会击退、仍会调回调）。 */
        public float getDamage() {
            return this.damage;
        }

        @Nullable
        public DamageSource getDamageSource() {
            return this.damageSource;
        }

        public boolean isRequireLineOfSight() {
            return this.requireLineOfSight;
        }

        public boolean isIgnoreAllies() {
            return this.ignoreAllies;
        }

        public boolean isIgnoreCreative() {
            return this.ignoreCreative;
        }

        public boolean isIgnoreInvulnerable() {
            return this.ignoreInvulnerable;
        }

        public boolean isResetInvulnerableTime() {
            return this.resetInvulnerableTime;
        }

        public int getMaxTargets() {
            return this.maxTargets;
        }

        public float getKnockbackStrength() {
            return this.knockbackStrength;
        }

        public float getKnockbackUpward() {
            return this.knockbackUpward;
        }

        @Nullable
        public Predicate<LivingEntity> getFilter() {
            return this.filter;
        }

        @Nullable
        public RangeAttackCallback getCallback() {
            return this.callback;
        }
    }

    /**
     * {@link Options} 的可变构造器 —— 链式调用描述一次范围攻击，{@link #build()} 定型成不可变快照；
     * 形状只能在创建时指定（它决定几何算法，中途改几乎一定是写错了）。
     */
    public static final class Builder {

        private final Shape shape;

        @Nullable
        private Vec3 origin;
        @Nullable
        private Vec3 direction;

        private float radius = 3.0F;
        private float innerRadius = 0.0F;
        private float angleDegrees = 90.0F;
        private float verticalRange = 1.5F;
        private float damage = 0.0F;
        @Nullable
        private DamageSource damageSource;
        private boolean requireLineOfSight = false;
        private boolean ignoreAllies = true;
        private boolean ignoreCreative = true;
        private boolean ignoreInvulnerable = true;
        private boolean resetInvulnerableTime = false;
        private int maxTargets = Integer.MAX_VALUE;
        private float knockbackStrength = 0.0F;
        private float knockbackUpward = 0.0F;
        @Nullable
        private Predicate<LivingEntity> filter;
        @Nullable
        private RangeAttackCallback callback;

        public Builder(Shape shape) {
            this.shape = Objects.requireNonNull(shape, "shape");
        }

        public Builder origin(@Nullable Vec3 origin) {
            this.origin = origin;
            return this;
        }

        /** 形状朝向，{@link #build()} 时归一化；SECTOR / ANNULUS 只取水平分量，CONE 用完整三维方向。 */
        public Builder direction(@Nullable Vec3 direction) {
            this.direction = direction;
            return this;
        }

        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }

        public Builder innerRadius(float innerRadius) {
            this.innerRadius = innerRadius;
            return this;
        }

        /** 扇面/锥体的<b>全角</b>（度），不是半角：90 表示左右各 45°；{@code >= 360} 表示不做角度限制。 */
        public Builder angleDegrees(float angleDegrees) {
            this.angleDegrees = angleDegrees;
            return this;
        }

        /** 垂直容差，默认 1.5（约一个玩家身高）：碰撞箱与 {@code origin.y ± verticalRange} 有交集才算命中。 */
        public Builder verticalRange(float verticalRange) {
            this.verticalRange = verticalRange;
            return this;
        }

        public Builder damage(float damage) {
            this.damage = damage;
            return this;
        }

        public Builder damageSource(@Nullable DamageSource damageSource) {
            this.damageSource = damageSource;
            return this;
        }

        /** 默认 false：范围攻击本就是“隔着掩体扫一片”；视线检测是射线追踪，所以放在几何判定<b>之后</b>才做。 */
        public Builder requireLineOfSight(boolean requireLineOfSight) {
            this.requireLineOfSight = requireLineOfSight;
            return this;
        }

        public Builder ignoreAllies(boolean ignoreAllies) {
            this.ignoreAllies = ignoreAllies;
            return this;
        }

        public Builder ignoreCreative(boolean ignoreCreative) {
            this.ignoreCreative = ignoreCreative;
            return this;
        }

        public Builder ignoreInvulnerable(boolean ignoreInvulnerable) {
            this.ignoreInvulnerable = ignoreInvulnerable;
            return this;
        }

        /** 默认 false；打开后清零目标的 {@code invulnerableTime}，避免原版 20 tick 无敌帧吃掉同一瞬间的第二段 AOE。 */
        public Builder resetInvulnerableTime(boolean resetInvulnerableTime) {
            this.resetInvulnerableTime = resetInvulnerableTime;
            return this;
        }

        public Builder maxTargets(int maxTargets) {
            this.maxTargets = maxTargets;
            return this;
        }

        public Builder knockbackStrength(float knockbackStrength) {
            this.knockbackStrength = knockbackStrength;
            return this;
        }

        public Builder knockbackUpward(float knockbackUpward) {
            this.knockbackUpward = knockbackUpward;
            return this;
        }

        public Builder filter(@Nullable Predicate<LivingEntity> filter) {
            this.filter = filter;
            return this;
        }

        public Builder callback(@Nullable RangeAttackCallback callback) {
            this.callback = callback;
            return this;
        }

        public boolean hasOrigin() {
            return this.origin != null;
        }

        public boolean hasDirection() {
            return this.direction != null;
        }

        public Shape getShape() {
            return this.shape;
        }

        /**
         * 定型成不可变 {@link Options}：归一化 direction、钳制各数值参数（360 保留为“不限制角度”的哨兵值）。
         * <p>刻意<b>不抛异常</b>：配错时“退化成打不到人”比“运行时崩掉一个实体 tick”好排查得多。
         */
        public Options build() {
            Vec3 resolvedOrigin = this.origin != null ? this.origin : Vec3.ZERO;

            Vec3 resolvedDirection;
            if (this.direction == null || this.direction.lengthSqr() <= DEGENERATE_SQR) {
                resolvedDirection = DEFAULT_DIRECTION;
            } else {
                resolvedDirection = this.direction.normalize();
            }

            return new Options(this, resolvedOrigin, resolvedDirection);
        }
    }

    public static Builder sector() {
        return new Builder(Shape.SECTOR);
    }

    public static Builder cone() {
        return new Builder(Shape.CONE);
    }

    public static Builder sphere() {
        return new Builder(Shape.SPHERE);
    }

    public static Builder cylinder() {
        return new Builder(Shape.CYLINDER);
    }

    public static Builder annulus() {
        return new Builder(Shape.ANNULUS);
    }

    public static int attackSector(LivingEntity attacker, float radius, float angleDegrees,
                                   float verticalRange, float damage) {
        return perform(attacker, sector()
                .radius(radius)
                .angleDegrees(angleDegrees)
                .verticalRange(verticalRange)
                .damage(damage));
    }

    /** 锥形范围攻击（一行搞定）：direction 默认<b>完整三维视线</b>（锥体是 3D 的，抬头低头要影响朝向）。 */
    public static int attackCone(LivingEntity attacker, float radius, float angleDegrees, float damage) {
        return perform(attacker, cone()
                .radius(radius)
                .angleDegrees(angleDegrees)
                .damage(damage));
    }

    public static int attackSphere(LivingEntity attacker, float radius, float damage) {
        return perform(attacker, sphere()
                .radius(radius)
                .damage(damage));
    }

    /** 执行一次范围攻击（回调走 Builder 的设置或攻击者自身）。@return 被处理的目标数 */
    public static int perform(LivingEntity attacker, Builder builder) {
        return perform(attacker, builder, null);
    }

    /**
     * 执行一次范围攻击 —— 本类的主入口。<b>只在服务端生效</b>：客户端调用直接返回 0，避免双端各结算一次造成双倍伤害。
     * <p>回调解析顺序：显式参数 → {@link Builder#callback} → 攻击者自身实现 {@link RangeAttackCallback} → {@link RangeAttackCallback#DEFAULT}。
     * <p>每个目标：几何命中 → 通用过滤（存活/创造/无敌/友军/自定义）→ beforeHit → modifyDamage → hurt（伤害 ≤ 0 跳过）→ 击退 → afterHit。
     * @return 被处理的目标数 —— 即通过了 {@link RangeAttackCallback#beforeHit} 的目标数
     */
    public static int perform(LivingEntity attacker, Builder builder, @Nullable RangeAttackCallback callback) {
        if (attacker == null || builder == null) return 0;
        if (attacker.level().isClientSide) return 0;
        return perform(attacker, fillAttackerDefaults(attacker, builder).build(), callback);
    }

    /** 用已定型的 {@link Options} 执行攻击（参数可每 tick 复用）；这条重载<b>不会</b>把 origin 回落到攻击者位置。 */
    public static int perform(LivingEntity attacker, Options options) {
        return perform(attacker, options, null);
    }

    private static int perform(LivingEntity attacker, Options options, @Nullable RangeAttackCallback callback) {
        if (attacker == null || options == null) return 0;
        if (attacker.level().isClientSide) return 0;

        RangeAttackCallback resolved = resolveCallback(attacker, options, callback);
        DamageSource source = options.getDamageSource() != null
                ? options.getDamageSource()
                : attacker.damageSources().mobAttack(attacker);

        // queryTargets 已经完成几何判定、通用过滤、排序与截断；这里只负责结算。
        List<LivingEntity> targets = queryTargets(attacker, options);
        int handled = 0;
        for (LivingEntity target : targets) {
            // 列表是快照：前面的回调可能已移除后面的目标（自爆、连锁闪电），所以结算前再确认一次。
            if (target.isRemoved() || !target.isAlive()) continue;

            if (!resolved.beforeHit(options, target)) continue;
            handled++;

            float damage = resolved.modifyDamage(options, target, options.getDamage());
            boolean hurtSuccess = false;
            if (damage > 0.0F) {
                // 原版无敌帧会把同一瞬间的第二段 AOE 直接吃掉（hurt 返回 false 且不掉血）。
                if (options.isResetInvulnerableTime()) {
                    target.invulnerableTime = 0;
                }
                hurtSuccess = target.hurt(source, damage);
            }

            // 先伤害后击退：击退会改 deltaMovement，放在 hurt 之后才不会影响伤害判定、也不被受击逻辑覆盖。
            applyKnockback(options, target);

            // 无论 hurt 是否成功都通知回调：音效/粒子/buff 对“免疫但这下扫到了”的目标也该播。
            resolved.afterHit(options, target, hurtSuccess);
        }
        return handled;
    }

    /**
     * 只查询候选目标、<b>不造成任何伤害</b> —— 给“范围预览”“AI 决策”用。
     * <p>返回列表按目标碰撞箱中心到 origin 的三维距离<b>升序</b>排列，并按 {@link Options#getMaxTargets()} 截断；客户端调用返回空列表。
     * <p>这条重载<b>不会</b>替你补 origin/direction：手动 build 且未设置 origin 时查询的是以世界原点为中心的球/扇 —— 最容易踩的坑。
     */
    public static List<LivingEntity> queryTargets(LivingEntity attacker, Options options) {
        if (attacker == null || options == null) return new ArrayList<>();
        if (attacker.level().isClientSide) return new ArrayList<>();
        if (options.getMaxTargets() <= 0) return new ArrayList<>();

        AABB search = broadPhaseBox(options);
        List<LivingEntity> candidates = attacker.level().getEntitiesOfClass(
                LivingEntity.class, search, entityFilter(attacker, options));

        List<LivingEntity> targets = new ArrayList<>(candidates.size());
        for (LivingEntity target : candidates) {
            AABB box = target.getBoundingBox();
            Vec3 center = box.getCenter();
            // 粗筛盒只保证不漏、不保证不多，多出来的候选在这里按角度/距离/内半径剔掉。
            if (!matches(options, center.x, center.y, center.z, box.minY, box.maxY)) continue;

            // 视线检测是射线追踪，比几何判定贵得多，所以放在几何之后。
            if (options.isRequireLineOfSight() && !attacker.hasLineOfSight(target)) continue;

            targets.add(target);
        }
        if (targets.isEmpty()) return new ArrayList<>();

        Vec3 origin = options.getOrigin();
        targets.sort(Comparator.comparingDouble((LivingEntity target) ->
                origin.distanceToSqr(target.getBoundingBox().getCenter())));

        int maxTargets = options.getMaxTargets();
        if (targets.size() > maxTargets) {
            return new ArrayList<>(targets.subList(0, maxTargets));
        }
        return targets;
    }

    /** {@link #queryTargets(LivingEntity, Options)} 的 Builder 版本：先按攻击者补全 origin / direction 再查询，与 perform 完全同一套默认值。 */
    public static List<LivingEntity> queryTargets(LivingEntity attacker, Builder builder) {
        if (attacker == null || builder == null) return new ArrayList<>();
        if (attacker.level().isClientSide) return new ArrayList<>();
        return queryTargets(attacker, fillAttackerDefaults(attacker, builder).build());
    }

    /**
     * 纯几何判定：点是否落在范围内。与命中判定共用同一份实现，所以特效预览与实际伤害范围<b>逐点一致</b>；
     * 不碰世界、不造成伤害，客户端做本地表现可安全调用；这里把目标当“点”（垂直只看 {@code point.y}），按实体判定请用 {@link #queryTargets}。
     */
    public static boolean contains(Options options, Vec3 point) {
        if (options == null || point == null) return false;
        // 点兼作“中心”与“垂直区间”（区间退化成一个点），走的仍是实体判定那段代码。
        return matches(options, point.x, point.y, point.z, point.y, point.y);
    }

    /**
     * 精确的几何判定 —— <b>唯一</b>的一份实现：实体查询传碰撞箱中心 + 垂直区间，{@link #contains} 把点兼作中心与区间，
     * 两者走同一段代码，所以“预览范围”和“实际命中”不会出现分歧。
     */
    private static boolean matches(Options options, double pointX, double pointY, double pointZ,
                                   double minY, double maxY) {
        Vec3 origin = options.getOrigin();
        double dx = pointX - origin.x;
        double dy = pointY - origin.y;
        double dz = pointZ - origin.z;
        double horizontalSqr = dx * dx + dz * dz;
        double distanceSqr = horizontalSqr + dy * dy;
        double radiusSqr = (double) options.getRadius() * options.getRadius();

        // 用平方比较，省掉每次判定的开方；switch 覆盖全部形状（新增枚举值会编译报错提醒补分支）。
        return switch (options.getShape()) {
            case SECTOR -> horizontalSqr <= radiusSqr
                    && withinHorizontalAngle(options, dx, dz)
                    && withinVerticalBand(options, minY, maxY);
            case ANNULUS -> horizontalSqr <= radiusSqr
                    && horizontalSqr >= (double) options.getInnerRadius() * options.getInnerRadius()
                    && withinHorizontalAngle(options, dx, dz)
                    && withinVerticalBand(options, minY, maxY);
            case CONE -> distanceSqr <= radiusSqr
                    && withinConeAngle(options, dx, dy, dz);
            case SPHERE -> distanceSqr <= radiusSqr;
            case CYLINDER -> horizontalSqr <= radiusSqr
                    && withinVerticalBand(options, minY, maxY);
        };
    }

    /**
     * 垂直容差判定：目标垂直区间 {@code [minY, maxY]} 与 {@code origin.y ± verticalRange} 是否有交集（只用中心会“打得到脚、打不到头”）。
     */
    private static boolean withinVerticalBand(Options options, double minY, double maxY) {
        double originY = options.getOrigin().y;
        double range = options.getVerticalRange();
        return maxY >= originY - range && minY <= originY + range;
    }

    /**
     * 水平角度判定（SECTOR / ANNULUS）：比较夹角余弦 {@code (d · dir) / (|d| · |dir|) >= cos(angle / 2)}。
     * <p>退化情况：{@code angleDegrees >= 360} 直接通过；{@code <= 0} 永不通过；
     * 目标水平距离 ≈ 0（贴脸）视为通过；direction 水平分量 ≈ 0（方向完全竖直）视为不限制角度。
     */
    private static boolean withinHorizontalAngle(Options options, double dx, double dz) {
        float angle = options.getAngleDegrees();
        if (angle >= 360.0F) return true;
        if (angle <= 0.0F) return false;

        double horizontalSqr = dx * dx + dz * dz;
        if (horizontalSqr < DEGENERATE_SQR) return true;

        Vec3 direction = options.getDirection();
        double dirHorizontalSqr = direction.x * direction.x + direction.z * direction.z;
        if (dirHorizontalSqr < DEGENERATE_SQR) return true;

        double dot = (dx * direction.x + dz * direction.z) / Math.sqrt(horizontalSqr * dirHorizontalSqr);
        // 允许一点点超界：目标正好压在扇面边界上时，浮点误差不该决定生杀。
        return dot >= cosHalfAngle(angle) - ANGLE_EPSILON;
    }

    /** 三维角度判定（CONE）。direction 在 {@link Builder#build()} 里已归一化，所以分母只需要 {@code |d|}。 */
    private static boolean withinConeAngle(Options options, double dx, double dy, double dz) {
        float angle = options.getAngleDegrees();
        if (angle >= 360.0F) return true;
        if (angle <= 0.0F) return false;

        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (distanceSqr < DEGENERATE_SQR) return true;

        Vec3 direction = options.getDirection();
        double dot = (dx * direction.x + dy * direction.y + dz * direction.z) / Math.sqrt(distanceSqr);
        return dot >= cosHalfAngle(angle) - ANGLE_EPSILON;
    }

    /** 全角的一半的余弦 —— {@code angleDegrees} 是全角，比较的却是“半角以内”。 */
    private static double cosHalfAngle(float angleDegrees) {
        return Math.cos(angleDegrees * 0.5 * Mth.DEG_TO_RAD);
    }

    /**
     * 粗筛用的保守 AABB：{@code getEntitiesOfClass} 按 section 线性扫描、成本跟盒子体积相关，所以盒子要“刚好包住形状”：
     * SPHERE / CONE 三轴都放宽 radius，其余形状水平放宽 radius、垂直只要 verticalRange。
     */
    private static AABB broadPhaseBox(Options options) {
        Vec3 origin = options.getOrigin();
        double horizontal = options.getRadius() + BROAD_PHASE_MARGIN;
        double vertical = switch (options.getShape()) {
            case SPHERE, CONE -> horizontal;
            case SECTOR, CYLINDER, ANNULUS -> options.getVerticalRange() + BROAD_PHASE_MARGIN;
        };
        return new AABB(
                origin.x - horizontal, origin.y - vertical, origin.z - horizontal,
                origin.x + horizontal, origin.y + vertical, origin.z + horizontal);
    }

    /**
     * 便宜的通用过滤，直接作为 {@code getEntitiesOfClass} 的 predicate（放在粗筛阶段，早排除省下后面的精确几何/射线检测）。
     */
    private static Predicate<LivingEntity> entityFilter(LivingEntity attacker, Options options) {
        return target -> {
            // getEntitiesOfClass 会把攻击者自己也捞回来，必须显式排除，否则范围攻击会持续打自己（走 hurt 那条，护甲挡不住）。
            if (target == attacker) return false;
            if (target.isRemoved() || !target.isAlive()) return false;
            if (options.isIgnoreInvulnerable() && target.isInvulnerable()) return false;
            if (options.isIgnoreCreative() && target instanceof Player player
                    && (player.isCreative() || player.isSpectator())) return false;
            if (options.isIgnoreAllies() && !attacker.canAttack(target)) return false;

            Predicate<LivingEntity> custom = options.getFilter();
            return custom == null || custom.test(target);
        };
    }

    /**
     * 击退 —— 在伤害之后、回调之前执行。方向取 origin → 目标的水平方向（比“沿朝向推”更符合范围技能的直觉），
     * 目标压在 origin 上时改用 direction 的水平分量兜底；伤害为 0 或 {@code hurt} 失败也照常击退。
     */
    private static void applyKnockback(Options options, LivingEntity target) {
        float strength = options.getKnockbackStrength();
        float upward = options.getKnockbackUpward();
        if (strength <= 0.0F && upward <= 0.0F) return;

        if (strength > 0.0F) {
            Vec3 origin = options.getOrigin();
            double dx = target.getX() - origin.x;
            double dz = target.getZ() - origin.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < KNOCKBACK_MIN_HORIZONTAL) {
                Vec3 direction = options.getDirection();
                double dirHorizontal = Math.sqrt(
                        direction.x * direction.x + direction.z * direction.z);
                if (dirHorizontal > KNOCKBACK_MIN_HORIZONTAL) {
                    dx = direction.x / dirHorizontal;
                    dz = direction.z / dirHorizontal;
                } else {
                    dx = 0.0;
                    dz = 1.0;
                }
            } else {
                dx /= horizontal;
                dz /= horizontal;
            }
            target.knockback(strength, dx, dz);
        }

        if (upward > 0.0F) {
            // push 是“叠加”而不是“设置”，放在 knockback 之后才不会被后者的 setDeltaMovement 冲掉。
            target.push(0.0, upward, 0.0);
        }
    }

    /**
     * 把 Builder 里“未设置”的 origin / direction 换成攻击者身上的默认值（只有带 attacker 的入口才有“以攻击者为中心”的语义）。
     */
    private static Builder fillAttackerDefaults(LivingEntity attacker, Builder builder) {
        if (!builder.hasOrigin()) {
            // 碰撞箱中心而不是 position()（脚底）：用脚底会让扇形/球形整体下移半身，判定和视觉特效对不上。
            builder.origin(attacker.getBoundingBox().getCenter());
        }
        if (!builder.hasDirection()) {
            builder.direction(defaultDirection(attacker, builder.getShape()));
        }
        return builder;
    }

    /**
     * 默认朝向：CONE 用完整三维视线；其余形状用<b>水平化</b>视线（把 {@code getLookAngle()} 的 y 归零会在俯仰接近垂直时方向乱跳）。
     */
    private static Vec3 defaultDirection(LivingEntity attacker, Shape shape) {
        if (shape == Shape.CONE) {
            return attacker.getLookAngle();
        }
        return Vec3.directionFromRotation(0.0F, attacker.getYRot());
    }

    /** 解析本次攻击使用的回调：显式参数 → Builder 设置 → 攻击者自身实现 → {@link RangeAttackCallback#DEFAULT}。 */
    private static RangeAttackCallback resolveCallback(LivingEntity attacker, Options options,
                                                       @Nullable RangeAttackCallback explicit) {
        if (explicit != null) return explicit;

        RangeAttackCallback fromOptions = options.getCallback();
        if (fromOptions != null) return fromOptions;

        if (attacker instanceof RangeAttackCallback selfCallback) return selfCallback;

        return RangeAttackCallback.DEFAULT;
    }
}
