package org.bytechen.hall.event.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.compat.BCCoreCompat;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import org.bytechen.infcore.api.IInfectedEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 黑洞的引力与伤害 —— 全部逻辑都在这里，{@link BlackHoleEntity} 只是数据载体。
 *
 * <h3>引力</h3>
 * 每个 tick 对引力半径内的每个生物施加一个指向黑洞的速度增量：
 * <pre>
 *   M   = 本体碰撞箱体积 × 密度系数      （黑洞的“质量”，见 BlackHoleEntity#getColliderVolume）
 *   m   = 目标碰撞箱体积 × 密度系数      （目标质量，只用于质量比修正）
 *   r   = 目标到黑洞中心的距离
 *   F   = G · M · m / r²                 （牛顿万有引力）
 *   a   = F / m = G · M / r²             （加速度；距离越近越强，平方反比）
 *   Δv  = a · u                          （u 为指向黑洞的单位向量）
 *
 * 其中 a 再做 Plummer 正规化（见 {@link #CORE_RADIUS}）：远处仍是严格的
 * 平方反比，近处不再发散 —— 否则点质量的 1/r² 在 40 格外会小得毫无手感。
 *
 * 实际施加速度时再乘一个 <b>惯性因子 m/(m+m₀)</b>：重物更难被拉动，
 * 轻物几乎瞬间被吸走。纯牛顿解里质量会约掉（伽利略），但在游戏里
 * 让体积大的生物显得更“重”更合乎直觉，也让“质量按碰撞箱体积算”
 * 这一步真正产生可见差异。
 *
 * <h3>伤害</h3>
 * 进入事件视界的生物每 tick 受到固定的物理伤害。为绕过受击无敌帧
 * （否则每 tick 只能命中一次），命中前把 {@code invulnerableTime} 清零 ——
 * 与 {@code MeteoriteEntity} 的 AoE 做法一致。
 *
 * <h3>排除项</h3>
 * 实现 {@link IInfectedEntity} 且感染类型为 {@code hall:hall} 的生物
 * 既不被吸引也不受伤（同类免疫）。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHolePhysicsHandler {

    // ── 调参区 ──

    /**
     * 万有引力常数（游戏单位）。越大吸得越猛。
     * <p>与 {@link #CORE_RADIUS}、{@link #BLACK_HOLE_DENSITY} 一起决定强度，
     * 见 {@link #CORE_RADIUS} 的加速度表。</p>
     */
    public static final double G = 0.7;

    /** 碰撞箱体积 → 质量的密度系数（黑洞本体）：0.5³ × 24 → M ≈ 3.0。 */
    public static final double BLACK_HOLE_DENSITY = 24.0;

    /**
     * 引力“核心半径”r₀（格）——把黑洞从**点质量**正规化为有体积的球体。
     * <p>严格点质量的 1/r² 在远处小到无法感知（40 格处仅约 0.0008 格/tick²），
     * 整片引力场等于形同虚设。这里改用 <b>Plummer 球</b>形式的正规化引力：
     *
     * <pre>  a(r) = G·M · r / (r² + r₀²)^{3/2}</pre>
     *
     * 它在 r ≫ r₀ 时严格退化为牛顿的 G·M/r²（距离足够远时仍是平方反比），
     * 在 r ≪ r₀ 时内部近似线性、中心不发散 —— 相当于把质量摊在一个半径
     * r₀ 的球里，是真实天体物理处理“延展质量”的标准做法。</p>
     *
     * <p>取 M = 3.0、G = 0.7（GM = 2.1）、r₀ = 5 格时的裸加速度：</p>
     * <table>
     *   <tr><th>r</th><td>1</td><td>2</td><td>3</td><td>5</td><td>8</td><td>12</td><td>20</td><td>30</td><td>40</td></tr>
     *   <tr><th>a</th><td>0.016</td><td>0.027</td><td>0.032</td><td>0.030</td><td>0.020</td><td>0.012</td><td>0.0048</td><td>0.0022</td><td>0.0013</td></tr>
     * </table>
     * <p>峰值出现在 r ≈ r₀/√2 ≈ 3.5 格（也就是事件视界附近），r = 40 格时
     * 与纯牛顿 GM/r² 相差不到 3%。再乘生物惯性因子 m/(m+m₀)：
     * 僵尸(0.70)≈0.41、铁傀儡(4.9)≈0.83、恶魂(16)≈0.94。</p>
     * <p>累积效果（忽略生物自身阻力）：僵尸从 20 格被吸到中心约 6 秒，
     * 从 40 格约 16 秒 —— 越靠近越快，是典型的引力加速。</p>
     */
    public static final double CORE_RADIUS = 5.0;

    /** 碰撞箱体积 → 质量的密度系数（生物）。 */
    public static final double BODY_DENSITY = 1.0;

    /**
     * 惯性参考质量 m₀：越大则体积对“抗拉扯”的影响越明显。
     * <p>铁傀儡碰撞箱体积约 4.9，是僵尸（0.6×1.95×0.6≈0.70）的 7 倍，
     * 惯性因子 0.83 对 0.41 —— 同样的引力下铁傀儡明显更难被拖走。</p>
     */
    public static final double INERTIA_REFERENCE = 1.0;

    /** 每 tick 对事件视界内生物的伤害（点）。 */
    public static final float HORIZON_DAMAGE_PER_TICK = 20.0f;

    /** 单个生物每 tick 允许的最大速度增量（防止贴脸瞬间获得极大速度）。 */
    public static final double MAX_DV_PER_TICK = 0.45;

    /** 到黑洞中心小于该距离时不再按 r² 放大（避免除零与速度爆炸）。 */
    public static final double MIN_RADIUS = 0.35;

    private BlackHolePhysicsHandler() {}

    /**
     * 活跃黑洞登记表。
     * <p>
     * <b>为什么需要它</b>：以前这里用一个 {@code AABB(-3e7 ~ 3e7)} 去
     * {@code getEntitiesOfClass} 找黑洞。原版的实体查询是按 section 列<b>线性扫描</b>的
     * （{@code EntitySectionStorage.forEachAccessibleNonEmptySection} 会从 {@code minX}
     * 一路循环到 {@code maxX}，循环体里还各建一次 subSet / iterator），±3e7 的盒子等于
     * 每次调用都空跑约 375 万轮，三个维度就是每 tick 约 1125 万轮 —— 而且这个开销与
     * 世界里有没有实体<b>无关</b>，能把 TPS 直接压到 10 左右。
     * <p>
     * 谁生成黑洞谁登记、谁移除谁注销，处理器只遍历这张表，成本变成 O(活跃黑洞数)。
     */
    private static final Set<BlackHoleEntity> ACTIVE = ConcurrentHashMap.newKeySet();

    /** 生成黑洞时登记（由 {@link BlackHoleEntity} 在服务端构造时调用）。 */
    public static void register(BlackHoleEntity hole) {
        if (hole != null) ACTIVE.add(hole);
    }

    /** 黑洞被移除/丢弃时注销。 */
    public static void unregister(BlackHoleEntity hole) {
        if (hole != null) ACTIVE.remove(hole);
    }

    /** 关服清空，避免静态表跨存档残留。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!event.side.isServer()) return;
        if (event.phase != TickEvent.Phase.END) return;

        Level level = event.level;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 没有黑洞 → 什么都不做（这就是登记表存在的意义）
        if (ACTIVE.isEmpty()) return;

        for (BlackHoleEntity hole : ACTIVE) {
            if (hole.isRemoved() || !hole.isAlive()) continue;
            if (hole.level() != serverLevel) continue;
            if (!serverLevel.hasChunkAt(hole.blockPosition())) continue;   // 区块已卸载
            applyGravity(serverLevel, hole);
            applyHorizonDamage(serverLevel, hole);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 引力
    // ══════════════════════════════════════════════════════════

    private static void applyGravity(ServerLevel level, BlackHoleEntity hole) {
        float gravityRadius = hole.getGravityRadius();
        if (gravityRadius <= 0.0f) return;

        Vec3 center = hole.position();
        AABB search = new AABB(center, center).inflate(gravityRadius);
        double radiusSqr = (double) gravityRadius * gravityRadius;

        // 黑洞质量 M = 碰撞箱体积 × 密度系数
        double holeMass = Math.max(hole.getColliderVolume(), 1.0e-4) * BLACK_HOLE_DENSITY;
        double gM = G * holeMass;
        if (gM <= 0.0) return;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, search,
                e -> e.isAlive() && !isExcluded(e));

        for (LivingEntity target : targets) {
            Vec3 toHole = center.subtract(target.position());
            double distSqr = toHole.lengthSqr();
            if (distSqr > radiusSqr) continue;
            if (distSqr < 1.0e-6) continue;

            double dist = Math.sqrt(distSqr);
            double r = Math.max(dist, MIN_RADIUS);

            // 牛顿万有引力 F = G·M·m / r²  →  加速度 a = F/m = G·M / r²
            // 再做 Plummer 正规化：a = G·M · r / (r² + r₀²)^{3/2}
            //   r ≫ r₀ 时退化为严格的平方反比；r ≪ r₀ 时中心不发散。
            double rr = r * r;
            double r0sq = CORE_RADIUS * CORE_RADIUS;
            double denom = Math.pow(rr + r0sq, 1.5);
            double accel = (denom > 1.0e-9) ? (gM * r / denom) : 0.0;

            // 惯性因子：体积越大越难被拉动
            double bodyMass = massOf(target);
            double inertia = bodyMass / (bodyMass + INERTIA_REFERENCE);

            double dv = Math.min(accel * inertia, MAX_DV_PER_TICK);

            Vec3 dir = toHole.scale(1.0 / dist);
            Vec3 velocity = target.getDeltaMovement().add(dir.scale(dv));

            // 轻物很快被拉成高速，钳一下避免穿透方块
            double speed = velocity.length();
            if (speed > 4.0) velocity = velocity.scale(4.0 / speed);

            target.setDeltaMovement(velocity);
            // 被拖拽时不应立刻被自身的落地摩擦抹掉这个速度增量
            target.hurtMarked = true;
            target.hasImpulse = true;
        }
    }

    // ══════════════════════════════════════════════════════════
    // 事件视界伤害
    // ══════════════════════════════════════════════════════════

    private static void applyHorizonDamage(ServerLevel level, BlackHoleEntity hole) {
        float killRadius = hole.getKillRadius();
        if (killRadius <= 0.0f) return;

        // ── 技能黑洞：VitalProbe 改血窗口 ──
        // damagePerTick > 0 表示这个黑洞是技能召唤物：伤害走改血（可穿透抗改血实现）。
        // 伤害只在 damageTicks 内生效，窗口结束后黑洞仍然存在、仍然有引力，只是不再削血。
        float bcDamage = hole.getBcDamagePerTick();
        int bcTicks = hole.getBcDamageTicks();
        boolean bcMode = bcDamage > 0.0f;
        if (bcMode && bcTicks <= 0) return;

        Vec3 center = hole.position().add(0.0, hole.getBbHeight() * 0.5, 0.0);
        AABB search = new AABB(center, center).inflate(killRadius);
        double radiusSqr = (double) killRadius * killRadius;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, search,
                e -> e.isAlive() && !isExcluded(e));

        for (LivingEntity target : targets) {
            // 用碰撞箱最近点判定，避免大体型生物“中心在外面”却已完全贴住黑洞
            double distSqr = distanceSqrToBox(target, center);
            if (distSqr > radiusSqr) continue;

            if (bcMode) {
                // 改血：直接写血量存储 —— 不受无敌帧/护甲/伤害抗性影响，
                // 且算到 ≤0 时由 BCCoreCompat 负责"终结"，不留浮点残血
                BCCoreCompat.damage(target, bcDamage, level.damageSources().magic());
            } else {
                // 清零无敌帧，保证每 tick 都能吃到 20 点 —— 与 MeteoriteEntity 的 AoE 一致
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().magic(), HORIZON_DAMAGE_PER_TICK);
            }
        }

        // 伤害窗口按"黑洞"而不是"命中"计时：没有目标也照走，这样 60 tick 就是 60 tick
        if (bcMode) hole.setBcDamageTicks(bcTicks - 1);
    }

    // ══════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════

    /** 生物的“质量”：碰撞箱体积 × 密度系数。 */
    private static double massOf(Entity entity) {
        return colliderVolume(entity) * BODY_DENSITY;
    }

    /** 碰撞箱体积 = 宽 × 高 × 深。 */
    private static double colliderVolume(Entity entity) {
        return Math.max(entity.getBbWidth(), 1.0e-3)
                * Math.max(entity.getBbHeight(), 1.0e-3)
                * Math.max(entity.getBbWidth(), 1.0e-3);
    }

    /** 点到实体碰撞箱的最近距离平方（实体完全包住该点时返回 0）。 */
    private static double distanceSqrToBox(Entity entity, Vec3 point) {
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 排除规则：
     * <ol>
     *   <li>创造模式与旁观者玩家 —— 完全免疫引力与伤害；</li>
     *   <li>实现 {@link IInfectedEntity} 且感染类型为 {@code hall:hall}
     *       的生物（同类免疫）；</li>
     *   <li>黑洞自身，避免互相干扰。</li>
     * </ol>
     */
    private static boolean isExcluded(Entity entity) {
        // 创造 / 旁观玩家豁免
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return true;
        }

        // hall 感染生物豁免
        if (entity instanceof IInfectedEntity infected) {
            ResourceLocation type = infected.getInfectionType();
            if (type != null
                    && HallMod.MODID.equals(type.getNamespace())
                    && "hall".equals(type.getPath())) {
                return true;
            }
        }

        // 黑洞之间互不影响
        return entity instanceof BlackHoleEntity;
    }

    /**
     * 计算某个黑洞的质量（碰撞箱体积 × 密度系数），供生成方写入实体。
     * 放在这里是为了让“质量定义”只有一处。
     */
    public static float computeBlackHoleMass(BlackHoleEntity hole) {
        return (float) (colliderVolume(hole) * BLACK_HOLE_DENSITY);
    }
}
