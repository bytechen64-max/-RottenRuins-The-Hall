package org.bytechen.rottenruins.event.impl;

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
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.utils.entity.DirectHealth;
import org.bytechen.rottenruins.api.anomaly.AnomalyDamageSource;
import org.bytechen.rottenruins.content.anomaly.HallAnomalyAttacks;
import net.minecraft.world.damagesource.DamageSource;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.BlackHoleEntity;
import org.bytechen.rottenruins.api.infection.IInfectedEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

/**
 * 黑洞的引力与伤害集中在这里，{@link BlackHoleEntity} 只是数据载体。
 * 加速度为 Plummer 正规化后的 GM·r/(r²+r₀²)^{3/2}，再乘惯性因子 m/(m+m₀)。
 * 视界内伤害先清 invulnerableTime，否则每 tick 只能命中一次。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHolePhysicsHandler {

    /** 万有引力常数（游戏单位）；与 {@link #CORE_RADIUS}、{@link #BLACK_HOLE_DENSITY} 共同决定强度。 */
    public static final double G = 0.7;

    /** 碰撞箱体积 → 质量的密度系数（黑洞本体）：0.5³ × 24 → M ≈ 3.0。 */
    public static final double BLACK_HOLE_DENSITY = 24.0;

    /** 引力核心半径 r₀（格）：Plummer 正规化，中心不发散且 r≫r₀ 时退化为平方反比。 */
    public static final double CORE_RADIUS = 5.0;

    /** 碰撞箱体积 → 质量的密度系数（生物）。 */
    public static final double BODY_DENSITY = 1.0;

    /** 惯性参考质量 m₀：越大则碰撞箱体积对“抗拉扯”的影响越明显。 */
    public static final double INERTIA_REFERENCE = 1.0;

    /** 每 tick 对事件视界内生物的伤害（点）。 */
    public static final float HORIZON_DAMAGE_PER_TICK = 20.0f;

    /** 单个生物每 tick 允许的最大速度增量（防止贴脸瞬间获得极大速度）。 */
    public static final double MAX_DV_PER_TICK = 0.45;

    /** 到黑洞中心小于该距离时不再按 r² 放大（避免除零与速度爆炸）。 */
    public static final double MIN_RADIUS = 0.35;

    private BlackHolePhysicsHandler() {}

    /** 活跃黑洞登记表：避免 AABB(±3e7) 查询每 tick 空扫上千万 section，成本降为 O(活跃数)。 */
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

        if (ACTIVE.isEmpty()) return;

        for (BlackHoleEntity hole : ACTIVE) {
            if (hole.isRemoved() || !hole.isAlive()) continue;
            if (hole.level() != serverLevel) continue;
            if (!serverLevel.hasChunkAt(hole.blockPosition())) continue;   // 区块已卸载
            applyGravity(serverLevel, hole);
            applyHorizonDamage(serverLevel, hole);
        }
    }

    private static void applyGravity(ServerLevel level, BlackHoleEntity hole) {
        float gravityRadius = hole.getGravityRadius();
        if (gravityRadius <= 0.0f) return;

        Vec3 center = hole.position();
        AABB search = new AABB(center, center).inflate(gravityRadius);
        double radiusSqr = (double) gravityRadius * gravityRadius;

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

            // a = G·M·r/(r²+r₀²)^{3/2}（Plummer 正规化，中心不发散）
            double rr = r * r;
            double r0sq = CORE_RADIUS * CORE_RADIUS;
            double denom = Math.pow(rr + r0sq, 1.5);
            double accel = (denom > 1.0e-9) ? (gM * r / denom) : 0.0;

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

    private static void applyHorizonDamage(ServerLevel level, BlackHoleEntity hole) {
        float killRadius = hole.getKillRadius();
        if (killRadius <= 0.0f) return;

        // 技能黑洞：damagePerTick > 0 走改血，仅在 damageTicks 窗口内生效（之后仍有引力）
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

            DamageSource source = new AnomalyDamageSource(level.damageSources().magic(),
                    hole.getAnomalyAttacker(), HallAnomalyAttacks.BLACK_HOLE);
            if (bcMode) {
                // 改血绕过无敌帧/护甲/抗性，≤0 时由 DirectHealth 终结
                DirectHealth.damage(target, bcDamage, source);
            } else {
                // 清零无敌帧，保证每 tick 都能吃到 20 点 —— 与 MeteoriteEntity 的 AoE 一致
                target.invulnerableTime = 0;
                target.hurt(source, HORIZON_DAMAGE_PER_TICK);
            }
        }

        // 伤害窗口按"黑洞"而不是"命中"计时：没有目标也照走，这样 60 tick 就是 60 tick
        if (bcMode) hole.setBcDamageTicks(bcTicks - 1);
    }

    private static double massOf(Entity entity) {
        return colliderVolume(entity) * BODY_DENSITY;
    }

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

    /** 排除创造/旁观玩家、{@code hall:hall} 感染生物（同类免疫）与黑洞自身。 */
    private static boolean isExcluded(Entity entity) {
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return true;
        }

        // 王庭感染生物豁免（腐化系不吃这条王庭机制，后续按各自机制补）
        if (entity instanceof IInfectedEntity infected
                && RottenFaction.HALL.matches(infected.getInfectionType())) {
            return true;
        }

        return entity instanceof BlackHoleEntity;
    }

    /** 质量定义只留一处：生成方用它把质量写进实体。 */
    public static float computeBlackHoleMass(BlackHoleEntity hole) {
        return (float) (colliderVolume(hole) * BLACK_HOLE_DENSITY);
    }
}
