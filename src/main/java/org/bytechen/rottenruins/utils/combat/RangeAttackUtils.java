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
 * <b>范围攻击工具类</b> —— 给定“形状 + 参数”，一次找出范围内的生物，并统一结算伤害、击退与命中回调。
 *
 * <h3>它解决什么问题</h3>
 * <p>原版只提供 AABB 查询（{@code getEntitiesOfClass}），想做一个“扇形横扫”或“锥形吐息”，
 * 往往要在每个技能里各写一遍角度判定。更麻烦的是<b>特效预览</b>和<b>实际命中</b>
 * 通常各写一套判定，稍不留神就出现“特效扫过去了却没掉血”。<br>
 * 本类把几何判定抽成唯一的一份私有实现（{@code matches(...)}），
 * {@link #contains} 与实际命中走的是同一段代码，所以预览与结算天然一致。</p>
 *
 * <h3>支持的五种形状</h3>
 * <ul>
 *   <li>{@link Shape#SECTOR} <b>扇形</b>：以水平方向为轴的扇面。水平距离 ≤ radius，
 *       水平夹角 ≤ angleDegrees/2，垂直只要求目标碰撞箱与 origin.y ± verticalRange 相交。
 *       近战挥砍、地面冲击波用它 —— 判定不会因为目标站在坡上/台阶上就漏掉。</li>
 *   <li>{@link Shape#CONE} <b>锥形</b>：完整 3D 圆锥。目标中心相对 origin 的三维向量与
 *       三维 direction 的夹角 ≤ angleDegrees/2，且三维距离 ≤ radius。吐息、激光、俯冲用它。</li>
 *   <li>{@link Shape#SPHERE} <b>球形</b>：三维距离 ≤ radius，全向。</li>
 *   <li>{@link Shape#CYLINDER} <b>圆柱</b>：水平距离 ≤ radius + 垂直碰撞箱区间相交，全向。</li>
 *   <li>{@link Shape#ANNULUS} <b>环形扇区</b>：在 SECTOR 基础上再要求水平距离 ≥ innerRadius，
 *       做“甜甜圈冲击波”“贴脸不吃伤害”的技能用它。</li>
 * </ul>
 * <p>{@code angleDegrees >= 360} 一律视为<b>不做角度限制</b>，调用方不用为“全向”准备两套参数。</p>
 *
 * <h3>端侧 &amp; 重复伤害</h3>
 * <p>伤害、击退、回调<b>只在服务端</b>执行：客户端调 {@link #perform} 直接返回 0，
 * {@link #queryTargets} 返回空列表。血量由服务端 {@code hurt} 负责同步，
 * 所以不会出现“两端各打一次 = 双倍伤害”。客户端要做命中表现请自己发包。</p>
 *
 * <h3>一次典型调用</h3>
 * <pre>{@code
 * // 面前 90° 扇形、4 格、垂直容差 1.5、伤害 8、水平击退 0.4
 * RangeAttackUtils.perform(this, RangeAttackUtils.sector()
 *         .radius(4.0f)
 *         .angleDegrees(90.0f)
 *         .verticalRange(1.5f)
 *         .damage(8.0f)
 *         .knockbackStrength(0.4f));
 * }</pre>
 * <p>生物类 {@code implements RangeAttackCallback} 后，上面这次调用会自动带上它自己的
 * {@code beforeHit / modifyDamage / afterHit} 逻辑，无需再传回调。</p>
 *
 * <h3>关于性能</h3>
 * <p>实体查询本身是按 section 线性扫描的（{@code BlackHolePhysicsHandler} 里踩过这个坑），
 * 成本跟外包 AABB 的体积直接相关。所以这里只用一个“刚好包住形状”的保守盒子粗筛，
 * 精确的角度/距离判定全在内存里做 —— 不会为每个实体单独查询一次世界。</p>
 */
public final class RangeAttackUtils {

    /**
     * 角度比较容差：目标正好卡在扇面边缘时，浮点误差不该决定“这一下算不算打中”。
     */
    private static final double ANGLE_EPSILON = 1.0E-6;

    /**
     * 粗筛 AABB 的微小外扩。
     * <p>原版 {@code AABB#intersects} 用的是<b>严格</b>不等号，零厚度的盒子
     * （例如 {@code radius = 0} 或 {@code verticalRange = 0}）与任何实体都不相交；
     * 稍微放宽一点，避免这种退化参数把候选全部漏掉。精确判定在后面，放宽不会多命中。</p>
     */
    private static final double BROAD_PHASE_MARGIN = 1.0E-3;

    /** 判定“向量退化到没有方向”的平方长度阈值。 */
    private static final double DEGENERATE_SQR = 1.0E-8;

    /** 目标正好压在 origin 上时，判断方向水平分量是否可用的阈值。 */
    private static final double KNOCKBACK_MIN_HORIZONTAL = 1.0E-4;

    /** direction 未设置或退化成零向量时的兜底轴向（+Z），保证角度判定永远有确定的轴。 */
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0.0, 0.0, 1.0);

    private RangeAttackUtils() {
    }

    // ══════════════════════════════════════════════════════════════
    //  形状
    // ══════════════════════════════════════════════════════════════

    /**
     * 命中范围的几何形状。
     * <p>每个形状具体用哪些参数见 {@link Options} 的类注释；这里只强调两个容易踩的点：</p>
     * <ul>
     *   <li><b>角度是全角</b>，不是半角：{@code angleDegrees = 90} 表示正前方左右各 45°；</li>
     *   <li>SECTOR / ANNULUS 的垂直判定用的是目标<b>碰撞箱</b>与
     *       {@code [origin.y - verticalRange, origin.y + verticalRange]} 是否相交，
     *       而不是“目标中心是否在带内” —— 否则高个生物的脚被打到了、头却判定不中。</li>
     * </ul>
     */
    public enum Shape {
        /** 水平扇形（二维扇面 + 垂直容差）。 */
        SECTOR,
        /** 三维圆锥。 */
        CONE,
        /** 三维球形。 */
        SPHERE,
        /** 竖直圆柱。 */
        CYLINDER,
        /** 环形扇区：SECTOR 再挖掉 innerRadius 以内的区域。 */
        ANNULUS
    }

    // ══════════════════════════════════════════════════════════════
    //  参数快照
    // ══════════════════════════════════════════════════════════════

    /**
     * 一次范围攻击的<b>不可变</b>参数快照。
     * <p>只能由 {@link Builder#build()} 产生：build 里统一做钳制与 direction 归一化，
     * 之后所有几何判定都直接相信这些已校验的值，不必每次再判断一遍。</p>
     *
     * <p>默认值（Builder 的初始值）：</p>
     * <ul>
     *   <li>{@code radius = 3}、{@code innerRadius = 0}、{@code angleDegrees = 90}、
     *       {@code verticalRange = 1.5}；</li>
     *   <li>{@code damage = 0}、{@code knockbackStrength = 0}、{@code knockbackUpward = 0}；</li>
     *   <li>{@code requireLineOfSight = false}、{@code ignoreAllies = true}、
     *       {@code ignoreCreative = true}、{@code ignoreInvulnerable = true}、
     *       {@code resetInvulnerableTime = false}；</li>
     *   <li>{@code maxTargets = Integer.MAX_VALUE}、{@code filter = null}、
     *       {@code callback = null}、{@code damageSource = null}。</li>
     * </ul>
     */
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

        /**
         * 只允许 {@link Builder#build()} 调用。
         *
         * @param builder   参数来源
         * @param origin    已解析的原点（未设置时为 {@link Vec3#ZERO}）
         * @param direction 已归一化的朝向
         */
        private Options(Builder builder, Vec3 origin, Vec3 direction) {
            this.shape = builder.shape;
            this.origin = origin;
            this.direction = direction;
            this.radius = Math.max(0.0F, builder.radius);

            // 环带宽度必须为正：innerRadius >= radius 会让 ANNULUS 退化成“整个扇面”，
            // 与“环形 / 贴脸不吃伤害”的意图正好相反，所以按“贴边”处理（宽度趋近 0）。
            float inner = Math.max(0.0F, builder.innerRadius);
            if (inner >= this.radius) {
                inner = Math.max(0.0F, this.radius - 1.0E-3F);
            }
            this.innerRadius = inner;

            // 360 是“不限制角度”的哨兵值，所以上限就钳到 360；负角度没有意义，钳到 0。
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

        /** 几何原点（扇面顶点 / 球心 / 圆柱轴心）。未显式设置时是 {@link Vec3#ZERO}。 */
        public Vec3 getOrigin() {
            return this.origin;
        }

        /** 已归一化的朝向。SECTOR / ANNULUS 只用它的水平分量，CONE 用完整三维方向。 */
        public Vec3 getDirection() {
            return this.direction;
        }

        /** 外半径（扇形/圆柱的是水平半径，球/锥的是三维半径）。 */
        public float getRadius() {
            return this.radius;
        }

        /** ANNULUS 的内半径（其它形状用不到）；保证 {@code < getRadius()}，除非 radius 本身为 0。 */
        public float getInnerRadius() {
            return this.innerRadius;
        }

        /** 全角（不是半角），单位度；{@code 360} 表示不做角度限制。 */
        public float getAngleDegrees() {
            return this.angleDegrees;
        }

        /** SECTOR / CYLINDER / ANNULUS 的垂直容差：碰撞箱与 origin.y ± verticalRange 相交才算命中。 */
        public float getVerticalRange() {
            return this.verticalRange;
        }

        /** 基础伤害；{@code <= 0} 时不调用 {@code hurt}（但仍会击退、仍会调回调）。 */
        public float getDamage() {
            return this.damage;
        }

        /** 自定义伤害来源；{@code null} 表示用 {@code attacker.damageSources().mobAttack(attacker)}。 */
        @Nullable
        public DamageSource getDamageSource() {
            return this.damageSource;
        }

        /** 是否要求攻击者与目标之间无遮挡（{@code attacker.hasLineOfSight(target)}）。 */
        public boolean isRequireLineOfSight() {
            return this.requireLineOfSight;
        }

        /** 是否用 {@code attacker.canAttack(target)} 过滤友军（同队、同类友好关系）。 */
        public boolean isIgnoreAllies() {
            return this.ignoreAllies;
        }

        /** 是否跳过创造 / 旁观玩家。 */
        public boolean isIgnoreCreative() {
            return this.ignoreCreative;
        }

        /** 是否跳过 {@code isInvulnerable()} 的目标。 */
        public boolean isIgnoreInvulnerable() {
            return this.ignoreInvulnerable;
        }

        /** 是否在 {@code hurt} 前把目标的 {@code invulnerableTime} 清零。 */
        public boolean isResetInvulnerableTime() {
            return this.resetInvulnerableTime;
        }

        /** 最多处理多少个目标（按距离升序截断）；{@code <= 0} 视为不处理。 */
        public int getMaxTargets() {
            return this.maxTargets;
        }

        /** 水平击退强度，0 表示不击退。 */
        public float getKnockbackStrength() {
            return this.knockbackStrength;
        }

        /** 额外向上推力，0 表示不推。 */
        public float getKnockbackUpward() {
            return this.knockbackUpward;
        }

        /** 自定义过滤（与其它忽略规则叠加：全部满足才命中）；{@code null} 表示不过滤。 */
        @Nullable
        public Predicate<LivingEntity> getFilter() {
            return this.filter;
        }

        /** 本次攻击的命中回调；{@code null} 表示在 perform 里按“攻击者 → DEFAULT”解析。 */
        @Nullable
        public RangeAttackCallback getCallback() {
            return this.callback;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  构造器
    // ══════════════════════════════════════════════════════════════

    /**
     * {@link Options} 的可变构造器 —— 用链式调用描述一次范围攻击，最后 {@link #build()} 定型。
     *
     * <p>为什么最终产物要拆成可变的 Builder + 不可变的 Options 两层：Builder 允许逐步拼参数
     * （读起来像自然语言），而 Options 在 {@code queryTargets} / {@code perform} / 回调之间反复传递，
     * 必须保证“判定用的参数”和“结算用的参数”是同一份、不会被中途改掉。</p>
     *
     * <p>形状只能在创建时指定（用 {@link RangeAttackUtils#sector()} 等工厂），
     * 因为形状决定了几何算法，中途改形状几乎一定是写错了。</p>
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

        /**
         * @param shape 命中形状（一般通过 {@link RangeAttackUtils#sector()} 等工厂获得）
         */
        public Builder(Shape shape) {
            this.shape = Objects.requireNonNull(shape, "shape");
        }

        /**
         * 几何原点（扇面顶点 / 球心 / 圆柱轴心）。
         * <p>不设置时：直接 {@link #build()} 会回落到世界原点 {@link Vec3#ZERO}；
         * 而走 {@code perform(attacker, builder)} / {@code queryTargets(attacker, builder)} 时，
         * 会自动换成攻击者碰撞箱中心 —— 绝大多数情况不用自己传。</p>
         */
        public Builder origin(@Nullable Vec3 origin) {
            this.origin = origin;
            return this;
        }

        /**
         * 形状朝向（{@link #build()} 时归一化）。
         * <p>SECTOR / ANNULUS 只取它的<b>水平分量</b>当扇面轴；CONE 用完整三维方向；
         * SPHERE / CYLINDER 各向同性，用不到它，但也允许传（方便统一构造）。</p>
         * <p>不设置时自动取攻击者的水平化视线；CONE 取完整视线 {@code getLookAngle()}。</p>
         */
        public Builder direction(@Nullable Vec3 direction) {
            this.direction = direction;
            return this;
        }

        /**
         * 外半径（SECTOR / ANNULUS / CYLINDER 是水平半径，CONE / SPHERE 是三维半径）。
         * <p>默认 3。{@code <= 0} 会被钳到 0，形状退化为空集（不会抛异常）。</p>
         */
        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }

        /**
         * ANNULUS 的内半径：水平距离小于它的目标不算命中。
         * <p>默认 0（等价于 SECTOR）。build 时钳制到 {@code [0, radius)}：填得比外半径还大
         * 说明参数写反了，此时按“贴边”处理而不是让整个环带消失。</p>
         */
        public Builder innerRadius(float innerRadius) {
            this.innerRadius = innerRadius;
            return this;
        }

        /**
         * 扇面/锥体的<b>全角</b>（度），不是半角：90 表示正前方左右各 45°。
         * <p>默认 90。{@code >= 360} 表示不做角度限制；负值钳到 0。</p>
         */
        public Builder angleDegrees(float angleDegrees) {
            this.angleDegrees = angleDegrees;
            return this;
        }

        /**
         * SECTOR / CYLINDER / ANNULUS 的垂直容差：目标碰撞箱与
         * {@code origin.y ± verticalRange} 有交集才算命中。
         * <p>默认 1.5 —— 大约一个玩家身高，站在坡上/台阶上的目标也打得到。
         * 纯平面技能可以设小一点，垂直判定过宽会让高低差很大的目标也被扫到。</p>
         */
        public Builder verticalRange(float verticalRange) {
            this.verticalRange = verticalRange;
            return this;
        }

        /**
         * 基础伤害；默认 0（不调 {@code hurt}，但仍击退、仍调回调）。
         * <p>0 伤害是有意义的用法：纯推力/纯检测技能只需要几何 + 击退 + 回调。</p>
         */
        public Builder damage(float damage) {
            this.damage = damage;
            return this;
        }

        /**
         * 自定义伤害来源；默认 {@code null}，即
         * {@code attacker.damageSources().mobAttack(attacker)}。
         * <p>想让伤害按别的 {@code DamageType} 结算（魔法、爆炸、火焰……）或者归给第三方时传它。</p>
         */
        public Builder damageSource(@Nullable DamageSource damageSource) {
            this.damageSource = damageSource;
            return this;
        }

        /**
         * 是否要求攻击者与目标之间无遮挡。
         * <p>默认 <b>false</b>：范围攻击本来就是“隔着掩体扫一片”，默认要求视线会让贴墙的目标
         * 莫名免疫，也与玩家对“挥砍扫到墙后”的预期相反。需要真实遮挡判定（例如穿墙检测）再打开。</p>
         * <p>视线检测是射线追踪，比几何判定贵，所以它放在几何判定<b>之后</b>才做。</p>
         */
        public Builder requireLineOfSight(boolean requireLineOfSight) {
            this.requireLineOfSight = requireLineOfSight;
            return this;
        }

        /**
         * 是否过滤友军，用 {@code attacker.canAttack(target)} 判定。
         * <p>默认 <b>true</b>：王庭生物之间不互相误伤。做“无差别自爆 / 献祭队友”的技能时关掉。</p>
         */
        public Builder ignoreAllies(boolean ignoreAllies) {
            this.ignoreAllies = ignoreAllies;
            return this;
        }

        /**
         * 是否跳过创造 / 旁观玩家。
         * <p>默认 <b>true</b>。范围伤害本身也绕不过原版的创造免疫，但“命中回调/粒子/击退”
         * 这些副作用会照样砸到创造玩家身上，所以默认直接从候选里剔除。</p>
         */
        public Builder ignoreCreative(boolean ignoreCreative) {
            this.ignoreCreative = ignoreCreative;
            return this;
        }

        /**
         * 是否跳过 {@code isInvulnerable()} 的目标（默认 true）。
         * <p>不跳过的话，这些目标虽然不会掉血，却会吃击退和回调（粒子/音效），看起来像“打中了但没伤害”。</p>
         */
        public Builder ignoreInvulnerable(boolean ignoreInvulnerable) {
            this.ignoreInvulnerable = ignoreInvulnerable;
            return this;
        }

        /**
         * 是否在 {@code hurt} 前把目标的 {@code invulnerableTime} 清零。
         * <p>默认 <b>false</b>。原版 20 tick 无敌帧会让“同一瞬间的第二段 AOE”被直接吃掉
         * （{@code hurt} 返回 false、不掉血）。打开后每个目标都能稳定吃到这一下，
         * 代价是它和别的伤害叠加时 DPS 会变高 —— 适合“一次横扫就是一个整体伤害”的技能。</p>
         */
        public Builder resetInvulnerableTime(boolean resetInvulnerableTime) {
            this.resetInvulnerableTime = resetInvulnerableTime;
            return this;
        }

        /**
         * 最多处理多少个目标（按距离升序截断），默认 {@link Integer#MAX_VALUE}。
         * <p>每个目标都要走一次 {@code hurt} + 回调，成本是线性的；大范围 AOE 建议设个上限，
         * 避免一发打爆一屏实体。{@code <= 0} 视为不处理。</p>
         */
        public Builder maxTargets(int maxTargets) {
            this.maxTargets = maxTargets;
            return this;
        }

        /**
         * 水平击退强度，默认 0（不击退）。
         * <p>方向是 origin → 目标的<b>水平</b>方向，走 {@code target.knockback(strength, nx, nz)}。
         * 目标正好压在 origin 上时（例如自身脚下爆开）改用 direction 的水平分量兜底，
         * 避免传 (0,0) 导致“打不动人”。击退在伤害之后、回调之前执行。</p>
         */
        public Builder knockbackStrength(float knockbackStrength) {
            this.knockbackStrength = knockbackStrength;
            return this;
        }

        /**
         * 额外向上推力（{@code target.push(0, upward, 0)}），默认 0。
         * <p>与水平击退叠加使用，做“掀飞”效果。注意原版会按目标的击退抗性打折。</p>
         */
        public Builder knockbackUpward(float knockbackUpward) {
            this.knockbackUpward = knockbackUpward;
            return this;
        }

        /**
         * 自定义过滤，在几何判定通过之后、回调之前生效；默认 {@code null}（不过滤）。
         * <p>与其它忽略规则是<b>叠加</b>关系：必须先通过存活/创造/无敌/友军这些通用过滤，
         * 再通过这里。适合“只打某阵营”“只打带某状态的目标”这类业务过滤。</p>
         */
        public Builder filter(@Nullable Predicate<LivingEntity> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * 本次攻击的命中回调，默认 {@code null}。
         * <p>解析优先级：{@code perform} 的显式 callback 参数 &gt; 这里的设置 &gt;
         * 攻击者自身实现了 {@link RangeAttackCallback} &gt; {@link RangeAttackCallback#DEFAULT}。</p>
         */
        public Builder callback(@Nullable RangeAttackCallback callback) {
            this.callback = callback;
            return this;
        }

        /** origin 是否被显式设置过（{@code perform/queryTargets} 靠它决定要不要回落到攻击者位置）。 */
        public boolean hasOrigin() {
            return this.origin != null;
        }

        /** direction 是否被显式设置过。 */
        public boolean hasDirection() {
            return this.direction != null;
        }

        /** 本 Builder 的形状（只在创建时确定，不能改）。 */
        public Shape getShape() {
            return this.shape;
        }

        /**
         * 定型成不可变 {@link Options}，同时做参数校验与钳制：
         * <ul>
         *   <li>direction：{@code null} 或零向量 → 兜底 +Z；否则归一化
         *       （之后所有角度计算都直接把它当单位向量用）；</li>
         *   <li>radius 钳到 {@code >= 0}；innerRadius 钳到 {@code [0, radius)}，
         *       内半径 ≥ 外半径时按贴边处理（{@code radius - 0.001}）；</li>
         *   <li>angleDegrees 钳到 {@code [0, 360]}（360 保留为“不限制角度”的哨兵值）；</li>
         *   <li>verticalRange / 击退参数钳到 {@code >= 0}，maxTargets 钳到 {@code >= 0}；</li>
         *   <li>origin：{@code null} → {@link Vec3#ZERO}
         *       （走带 attacker 的入口时会先回落到攻击者碰撞箱中心）。</li>
         * </ul>
         *
         * <p>刻意<b>不抛异常</b>：这些参数多半来自配置或技能表，配错时“退化成打不到人”
         * 比“运行时崩掉一个实体 tick”好排查得多。</p>
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

    // ══════════════════════════════════════════════════════════════
    //  静态工厂
    // ══════════════════════════════════════════════════════════════

    /** 水平扇形（二维扇面 + 垂直容差）。 */
    public static Builder sector() {
        return new Builder(Shape.SECTOR);
    }

    /** 三维圆锥。 */
    public static Builder cone() {
        return new Builder(Shape.CONE);
    }

    /** 三维球形。 */
    public static Builder sphere() {
        return new Builder(Shape.SPHERE);
    }

    /** 竖直圆柱。 */
    public static Builder cylinder() {
        return new Builder(Shape.CYLINDER);
    }

    /** 环形扇区（SECTOR 挖掉 innerRadius 以内）。 */
    public static Builder annulus() {
        return new Builder(Shape.ANNULUS);
    }

    // ══════════════════════════════════════════════════════════════
    //  便捷入口
    // ══════════════════════════════════════════════════════════════

    /**
     * 扇形范围攻击（一行搞定）：origin 默认攻击者碰撞箱中心，direction 默认水平化视线，
     * 一圈通用过滤 + 默认回调解析，返回被处理的目标数。
     *
     * @param radius        水平半径
     * @param angleDegrees  全角（度），{@code >= 360} 表示不做角度限制
     * @param verticalRange 垂直容差
     * @param damage        基础伤害（{@code <= 0} 时只击退/调回调）
     * @return 被处理的目标数（见 {@link #perform(LivingEntity, Builder, RangeAttackCallback)}）
     */
    public static int attackSector(LivingEntity attacker, float radius, float angleDegrees,
                                   float verticalRange, float damage) {
        return perform(attacker, sector()
                .radius(radius)
                .angleDegrees(angleDegrees)
                .verticalRange(verticalRange)
                .damage(damage));
    }

    /**
     * 锥形范围攻击（一行搞定）：origin 默认攻击者碰撞箱中心，
     * direction 默认<b>完整三维视线</b>（锥体是 3D 的，抬头低头要影响朝向）。
     *
     * @param radius       锥长（三维半径）
     * @param angleDegrees 锥的全角（度），{@code >= 360} 表示不做角度限制
     * @param damage       基础伤害
     * @return 被处理的目标数
     */
    public static int attackCone(LivingEntity attacker, float radius, float angleDegrees, float damage) {
        return perform(attacker, cone()
                .radius(radius)
                .angleDegrees(angleDegrees)
                .damage(damage));
    }

    /**
     * 球形范围攻击（一行搞定）：origin 默认攻击者碰撞箱中心，全向。
     *
     * @param radius 球半径
     * @param damage 基础伤害
     * @return 被处理的目标数
     */
    public static int attackSphere(LivingEntity attacker, float radius, float damage) {
        return perform(attacker, sphere()
                .radius(radius)
                .damage(damage));
    }

    // ══════════════════════════════════════════════════════════════
    //  执行
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行一次范围攻击（回调走 Builder 的设置或攻击者自身）。
     *
     * @return 被处理的目标数
     */
    public static int perform(LivingEntity attacker, Builder builder) {
        return perform(attacker, builder, null);
    }

    /**
     * 执行一次范围攻击 —— 本类的主入口。
     *
     * <h3>回调解析顺序</h3>
     * <ol>
     *   <li>参数 {@code callback} 非 {@code null} → 用它；</li>
     *   <li>否则 {@link Builder#callback} 设置过 → 用它；</li>
     *   <li>否则 {@code attacker} 实现了 {@link RangeAttackCallback} → 用攻击者本身
     *       （生物类只要 {@code implements} 一下，之后所有范围攻击都自动带自己的特殊命中逻辑）；</li>
     *   <li>否则 {@link RangeAttackCallback#DEFAULT}。</li>
     * </ol>
     *
     * <h3>结算流程（每个目标）</h3>
     * <pre>
     *   几何命中 → 通用过滤（存活/创造/无敌/友军/自定义） → beforeHit
     *        → modifyDamage → hurt（伤害 ≤ 0 跳过） → 击退 → afterHit
     * </pre>
     * <p>一次攻击有多个目标，所以 {@code hurtSuccess} 不作为返回值，而是通过
     * {@link RangeAttackCallback#afterHit} 的第三个参数逐目标交给回调。</p>
     *
     * <p>若 Builder 未设置 origin / direction，会先用攻击者碰撞箱中心与视线补全
     * （见 {@link Builder#hasOrigin()}）。</p>
     *
     * <p><b>只在服务端生效</b>：客户端调用直接返回 0，避免双端各结算一次造成双倍伤害。</p>
     *
     * @param callback 显式回调，可为 {@code null}
     * @return 被处理的目标数 —— 即通过了 {@link RangeAttackCallback#beforeHit} 的目标数
     *         （被几何/过滤排除、或被 {@code beforeHit} 拒绝的不算）
     */
    public static int perform(LivingEntity attacker, Builder builder, @Nullable RangeAttackCallback callback) {
        if (attacker == null || builder == null) return 0;
        if (attacker.level().isClientSide) return 0;
        return perform(attacker, fillAttackerDefaults(attacker, builder).build(), callback);
    }

    /**
     * 用已定型的 {@link Options} 执行攻击。
     * <p>适合“参数每 tick 复用”的场景：Options 是不可变快照，构造一次即可反复调用，
     * 不必每 tick 重新 build。注意这条重载<b>不会</b>把 origin 回落到攻击者位置
     * —— Options 里是什么就是什么（配合 {@link #queryTargets(LivingEntity, Options)}
     * 先预览再执行时，这正是想要的行为）。</p>
     */
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
            // 列表是快照：前面的回调完全可能顺带移除了后面的目标（例如自爆、连锁闪电），
            // 所以结算前再确认一次。已经没了的目标直接跳过，也不计入返回值。
            if (target.isRemoved() || !target.isAlive()) continue;

            if (!resolved.beforeHit(options, target)) continue;
            handled++;

            float damage = resolved.modifyDamage(options, target, options.getDamage());
            boolean hurtSuccess = false;
            if (damage > 0.0F) {
                // 原版无敌帧会把同一瞬间的第二段 AOE 直接吃掉（hurt 返回 false 且不掉血），
                // 需要“一次横扫 = 一个整体伤害”的技能可以显式清零。
                if (options.isResetInvulnerableTime()) {
                    target.invulnerableTime = 0;
                }
                hurtSuccess = target.hurt(source, damage);
            }

            // 先伤害后击退：击退会改 deltaMovement，放在 hurt 之后才不会影响伤害判定，
            // 也不会被 hurt 内部的受击逻辑覆盖。
            applyKnockback(options, target);

            // 无论 hurt 是否成功都通知回调：附加效果（音效、粒子、buff）对“免疫但这下扫到了”
            // 的目标往往也应该播；回调内部可用 hurtSuccess 自己区分。
            resolved.afterHit(options, target, hurtSuccess);
        }
        return handled;
    }

    // ══════════════════════════════════════════════════════════════
    //  查询 / 纯几何
    // ══════════════════════════════════════════════════════════════

    /**
     * 只查询候选目标、<b>不造成任何伤害</b> —— 给“范围预览”“AI 决策”用。
     *
     * <p>返回列表已按目标碰撞箱中心到 origin 的三维距离<b>升序</b>排列，并按
     * {@link Options#getMaxTargets()} 截断。列表是新的可修改 {@link ArrayList}，
     * 调用方可以再遍历/筛选（例如“只挑最近的 3 个再各放一道闪电”）。</p>
     *
     * <p><b>只在服务端有结果</b>：客户端调用返回空列表。</p>
     *
     * <p>注意这条重载不会替你把 origin/direction 补成攻击者位置（Options 已经定型）：
     * 想让默认值落到攻击者身上，用 {@link #queryTargets(LivingEntity, Builder)} 或
     * {@link #perform(LivingEntity, Builder)}。手动 build 且未设置 origin 时，
     * 查询的是以世界原点为中心的球/扇 —— 这是最容易踩的坑，所以默认建议走 Builder 重载。</p>
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
            // 粗筛之后再做精确几何：粗筛盒是“刚好包住形状”的保守包围盒，
            // 只能保证不漏，不能保证不多 —— 多出来的候选在这里被角度/距离/内半径剔掉。
            if (!matches(options, center.x, center.y, center.z, box.minY, box.maxY)) continue;

            // 视线检测是射线追踪，比几何判定贵得多，所以放在几何之后：只有真的在范围内的
            // 目标才值得去查一次遮挡。
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

    /**
     * {@link #queryTargets(LivingEntity, Options)} 的 Builder 版本：先按攻击者补全未设置的
     * origin / direction（与 {@code perform} 完全同一套默认值），再查询。
     * <p>适合“先预览再决定打不打”的 AI：预览用的 Options 与实际 perform 的 Options
     * 由同一份 Builder 参数产生，范围完全一致。</p>
     */
    public static List<LivingEntity> queryTargets(LivingEntity attacker, Builder builder) {
        if (attacker == null || builder == null) return new ArrayList<>();
        if (attacker.level().isClientSide) return new ArrayList<>();
        return queryTargets(attacker, fillAttackerDefaults(attacker, builder).build());
    }

    /**
     * 纯几何判定：一个点是否落在给定范围内。
     *
     * <p>与命中判定共用同一份实现，所以特效预览（粒子、剑气、范围指示圈）算出来的结果
     * 与实际伤害范围<b>逐点一致</b>。服务端/客户端都可以调用 —— 它不碰世界、不造成伤害，
     * 客户端用它做本地表现是安全的。</p>
     *
     * <p>注意这里把目标当“点”处理：垂直方向只看 {@code point.y} 是否在
     * {@code origin.y ± verticalRange} 内，不像实体那样用碰撞箱求交集。
     * 需要按实体判定请用 {@link #queryTargets}。</p>
     */
    public static boolean contains(Options options, Vec3 point) {
        if (options == null || point == null) return false;
        // 点兼作“中心”与“垂直区间”（区间退化成一个点），走的仍是实体判定那段代码。
        return matches(options, point.x, point.y, point.z, point.y, point.y);
    }

    // ══════════════════════════════════════════════════════════════
    //  内部实现
    // ══════════════════════════════════════════════════════════════

    /**
     * 精确的几何判定 —— <b>唯一</b>的一份实现。
     * <p>实体查询把碰撞箱中心 {@code (pointX, pointY, pointZ)} 和垂直区间
     * {@code [minY, maxY]} 传进来；{@link #contains} 把点兼作中心与垂直区间。
     * 两者走同一段代码，所以“预览范围”和“实际命中”不会出现分歧。</p>
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
     * 垂直容差判定：目标的垂直区间 {@code [minY, maxY]} 与
     * {@code origin.y ± verticalRange} 是否有交集。
     * <p>用区间相交而不是“中心是否在带内”，是为了让高个/矮个生物、以及站在坡上的目标
     * 都能被同一份参数公平地判定 —— 只看中心会出现“打得到脚、打不到头”的怪事。</p>
     */
    private static boolean withinVerticalBand(Options options, double minY, double maxY) {
        double originY = options.getOrigin().y;
        double range = options.getVerticalRange();
        return maxY >= originY - range && minY <= originY + range;
    }

    /**
     * 水平角度判定（SECTOR / ANNULUS）。
     * <p>比较目标水平方向与 direction 水平分量的夹角的余弦值：</p>
     * <pre>
     *   cos(夹角) = (d · dir) / (|d| · |dir|) >= cos(angle / 2)
     * </pre>
     * <p>几个退化情况的处理（都注释了“为什么”）：</p>
     * <ul>
     *   <li>{@code angleDegrees >= 360}：不做角度限制，直接通过；</li>
     *   <li>{@code angleDegrees <= 0}：0° 的扇面没有面积，永远不通过；</li>
     *   <li>目标水平距离 ≈ 0：点就落在轴上，夹角无定义，视为通过（否则贴脸目标反而不中）；</li>
     *   <li>direction 水平分量 ≈ 0（方向完全竖直）：没有水平轴可比，视为不限制角度。</li>
     * </ul>
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

    /**
     * 三维角度判定（CONE）。
     * <p>direction 在 {@link Builder#build()} 里已归一化，所以分母只需要 {@code |d|}。</p>
     */
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
     * 粗筛用的保守 AABB。
     * <p>原版没有“扇形查询”，只能先按 AABB 把附近实体捞回来，再在内存里精确判定。
     * 而 {@code getEntitiesOfClass} 是按 section 线性扫描的（成本跟盒子体积直接相关），
     * 所以盒子要“刚好包住形状”，不能无脑 inflate 一个大值：</p>
     * <ul>
     *   <li>SPHERE / CONE：任意方向都不超过 radius，三个轴都放宽 radius；</li>
     *   <li>SECTOR / CYLINDER / ANNULUS：水平放宽 radius，垂直只需要 verticalRange
     *       （垂直判定本来就是按这个区间做的）。</li>
     * </ul>
     * <p>再加 {@link #BROAD_PHASE_MARGIN} 兜底原版 AABB 的严格不等号（见该常量注释）。</p>
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
     * 便宜的通用过滤，直接作为 {@code getEntitiesOfClass} 的 predicate。
     * <p>放在粗筛阶段而不是结算阶段：这些判定（存活、无敌、创造、友军）都很便宜，
     * 早排除就不必在后面的精确几何 / 射线检测里再为它们花钱。</p>
     * <p>顺序按“从便宜到可能稍贵”排：存活 → 无敌 → 创造 → 友军 → 自定义。</p>
     */
    private static Predicate<LivingEntity> entityFilter(LivingEntity attacker, Options options) {
        return target -> {
            // getEntitiesOfClass 会把攻击者自己也捞回来（它就在原点），必须显式排除，
            // 否则范围攻击会持续打自己（走 hurt 那条，护甲挡不住）。
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
     * 击退 —— 在伤害之后、回调之前执行。
     * <p>方向取 origin → 目标的水平方向（“从爆点往外推”），这比“沿朝向推”更符合
     * 范围技能的直觉。目标正好压在 origin 上时改用 direction 的水平分量兜底，
     * 否则 {@code knockback(strength, 0, 0)} 等于没有方向，看起来像“打不动人”。</p>
     * <p>即使伤害为 0 或 {@code hurt} 失败也照常击退：纯推力技能不需要伤害。</p>
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
            // push 是“叠加”而不是“设置”，放在 knockback 之后才不会被后者的
            // setDeltaMovement 冲掉；同时保留 knockback 自带的水平分量。
            target.push(0.0, upward, 0.0);
        }
    }

    /**
     * 把 Builder 里“未设置”的 origin / direction 换成攻击者身上的默认值。
     * <p>为什么放在这里而不是塞进 {@link Options}：Options 是可复用的定型快照，
     * 不该在 build 时偷偷依赖“上次是谁调用它”；而“以攻击者为中心”只是
     * {@code perform} / {@code queryTargets} 这两个带 attacker 的入口才有的语义。</p>
     * <p>Builder 是可变的，这里就地补全（调用方之后继续用同一个 Builder
     * 打同一个攻击者的技能，通常正是想要的行为）。</p>
     */
    private static Builder fillAttackerDefaults(LivingEntity attacker, Builder builder) {
        if (!builder.hasOrigin()) {
            // 碰撞箱中心而不是 position()（脚底）：RangeAttackUtils 的几何都以“中心”为原点，
            // 用脚底会让扇形/球形整体下移半身，判定和视觉特效对不上。
            builder.origin(attacker.getBoundingBox().getCenter());
        }
        if (!builder.hasDirection()) {
            builder.direction(defaultDirection(attacker, builder.getShape()));
        }
        return builder;
    }

    /**
     * 默认朝向：CONE 用完整三维视线；其余形状用<b>水平化</b>视线。
     * <p>这里用 {@code getYRot()} 构造水平向量，而不是把 {@code getLookAngle()} 的 y 归零：
     * 抬头/低头接近垂直时后者的水平分量会非常小甚至为 0，归一化后方向会乱跳；
     * 而 {@code directionFromRotation(0, yRot)} 是稳定的水平朝向。</p>
     */
    private static Vec3 defaultDirection(LivingEntity attacker, Shape shape) {
        if (shape == Shape.CONE) {
            return attacker.getLookAngle();
        }
        return Vec3.directionFromRotation(0.0F, attacker.getYRot());
    }

    /**
     * 解析本次攻击使用的回调。
     * <p>顺序：显式参数 → Builder 设置 → 攻击者自身实现 → {@link RangeAttackCallback#DEFAULT}。
     * 这样调用方可以“偶尔用一次性回调覆盖”，而常态下让生物类自己实现即可。</p>
     */
    private static RangeAttackCallback resolveCallback(LivingEntity attacker, Options options,
                                                       @Nullable RangeAttackCallback explicit) {
        if (explicit != null) return explicit;

        RangeAttackCallback fromOptions = options.getCallback();
        if (fromOptions != null) return fromOptions;

        if (attacker instanceof RangeAttackCallback selfCallback) return selfCallback;

        return RangeAttackCallback.DEFAULT;
    }
}
