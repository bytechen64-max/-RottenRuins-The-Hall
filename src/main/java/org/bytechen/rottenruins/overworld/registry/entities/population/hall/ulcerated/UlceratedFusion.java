package org.bytechen.rottenruins.overworld.registry.entities.population.hall.ulcerated;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.infection.event.EntityEvolveEvent;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected.BaseInfectedEntity;

import java.util.Comparator;

/**
 * 溃烂纠察（{@link ScoutEntity}）+ 溃烂追蹤者（{@link PursuerEntity}）的合体规则。
 *
 * <p>与「5 只斥候合体成巨碑」同一套思路：双方都处于无索敌的闲逛状态、彼此贴到
 * {@value #SEARCH_RADIUS} 格内时，融合成一只王庭感染生物（畸骸系列）。产物按权重抽取：
 * 畸骸骷髅 50%、畸骸末影人 30%、畸骸玩家 20%——越"人形"的产物越稀有。</p>
 *
 * <p>竞争处理：合体前双方都要 {@link BaseUlceratedEntity#beginFusion()} 占位，
 * 任意一方已被别的合体占用就放弃本次；生成失败则 {@link BaseUlceratedEntity#cancelFusion()}
 * 解锁，大家继续走。</p>
 */
public final class UlceratedFusion {

    /** 双方允许的最大间距（格）。 */
    private static final double SEARCH_RADIUS = 4.0D;
    /** 竖直容差（格）。 */
    private static final double SEARCH_HEIGHT = 4.0D;
    private static final double SEARCH_RADIUS_SQR = SEARCH_RADIUS * SEARCH_RADIUS;

    private UlceratedFusion() {}

    /** 由斥候发起：找最近的追蹤者。 */
    public static boolean tryFuse(ScoutEntity scout) {
        if (!(scout.level() instanceof ServerLevel level) || !eligible(scout)) return false;
        AABB area = scout.getBoundingBox().inflate(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS);
        PursuerEntity partner = level.getEntitiesOfClass(PursuerEntity.class, area,
                        other -> eligible(other) && other.distanceToSqr(scout) <= SEARCH_RADIUS_SQR).stream()
                .min(Comparator.comparingDouble(scout::distanceToSqr))
                .orElse(null);
        return partner != null && fuse(level, scout, partner);
    }

    /** 由追蹤者发起：找最近的斥候。 */
    public static boolean tryFuse(PursuerEntity pursuer) {
        if (!(pursuer.level() instanceof ServerLevel level) || !eligible(pursuer)) return false;
        AABB area = pursuer.getBoundingBox().inflate(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS);
        ScoutEntity partner = level.getEntitiesOfClass(ScoutEntity.class, area,
                        other -> eligible(other) && other.distanceToSqr(pursuer) <= SEARCH_RADIUS_SQR).stream()
                .min(Comparator.comparingDouble(pursuer::distanceToSqr))
                .orElse(null);
        return partner != null && fuse(level, partner, pursuer);
    }

    /** 只允许"闲逛、没在假死、没被别的合体占用"的个体参与。 */
    private static boolean eligible(BaseUlceratedEntity entity) {
        if (!entity.isAlive() || entity.isFakeDying() || entity.isFusing()) return false;
        var target = entity.getTarget();
        return target == null || !target.isAlive();
    }

    private static boolean fuse(ServerLevel level, ScoutEntity scout, PursuerEntity pursuer) {
        if (!scout.beginFusion()) return false;
        if (!pursuer.beginFusion()) {
            scout.cancelFusion();
            return false;
        }

        EntityType<? extends BaseInfectedEntity> resultType = roll(level.random);
        BaseInfectedEntity result = resultType == null ? null : resultType.create(level);
        if (result == null) {
            scout.cancelFusion();
            pursuer.cancelFusion();
            return false;
        }

        double x = (scout.getX() + pursuer.getX()) * 0.5D;
        double y = (scout.getY() + pursuer.getY()) * 0.5D;
        double z = (scout.getZ() + pursuer.getZ()) * 0.5D;
        // 优先中心点，其次两边的站位；都被堵死才按中心硬放（与巨碑合体同一套兜底）。
        if (!canStandAt(result, x, y, z)) {
            if (canStandAt(result, scout.getX(), scout.getY(), scout.getZ())) {
                x = scout.getX(); y = scout.getY(); z = scout.getZ();
            } else if (canStandAt(result, pursuer.getX(), pursuer.getY(), pursuer.getZ())) {
                x = pursuer.getX(); y = pursuer.getY(); z = pursuer.getZ();
            }
        }
        result.moveTo(x, y, z, scout.getYRot(), 0.0F);
        if (scout.isPersistenceRequired() || pursuer.isPersistenceRequired()) {
            result.setPersistenceRequired();
        }
        if (!level.addFreshEntity(result)) {
            scout.cancelFusion();
            pursuer.cancelFusion();
            return false;
        }

        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 1.0D, z, 8, 0.6D, 0.6D, 0.6D, 0.02D);
        level.playSound(null, x, y, z, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.2F, 0.7F);

        scout.discard();
        pursuer.discard();
        MinecraftForge.EVENT_BUS.post(new EntityEvolveEvent(level, scout, result));
        return true;
    }

    /** 该坐标能不能站下这只产物（与巨碑合体的 {@code canStandAt} 同义）。 */
    private static boolean canStandAt(BaseInfectedEntity entity, double x, double y, double z) {
        entity.moveTo(x, y, z);
        return !entity.level().getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext();
    }

    /** 产物权重：畸骸骷髅 50 / 畸骸末影人 30 / 畸骸玩家 20。 */
    private static EntityType<? extends BaseInfectedEntity> roll(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 50) return EntityTypeRegistry.INF_SKELETON.get();
        if (roll < 80) return EntityTypeRegistry.INF_ENDERMAN.get();
        return EntityTypeRegistry.INF_PLAYER.get();
    }
}
