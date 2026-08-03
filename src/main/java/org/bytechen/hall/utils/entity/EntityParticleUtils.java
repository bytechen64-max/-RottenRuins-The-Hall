package org.bytechen.hall.utils.entity;


import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * 实体粒子生成工具类，用于在实体的碰撞箱范围内生成自定义粒子，
 * 支持速度控制、范围缩放、声音播放等功能。
 * <p>
 * 使用方式：
 * <pre>
 * ParticleConfig config = CorruptionEntityParticleUtil.builder()
 *         .particle(ParticleTypes.PORTAL)
 *         .count(50)
 *         .rangeScale(1.5f)                // 碰撞箱放大1.5倍
 *         .velocityRandomRange(0.2, 0.1, 0.2)  // 速度范围 (±0.2, ±0.1, ±0.2)
 *         .sound(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.0f)
 *         .build();
 *
 * // 在客户端执行（例如实体所在客户端侧）
 * CorruptionEntityParticleUtil.spawnParticles(entity, config);
 * </pre>
 */
public final class EntityParticleUtils {

    private EntityParticleUtils() {}

    /**
     * 开始构建粒子配置
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 根据配置为指定实体生成粒子（仅在客户端生效）
     *
     * @param entity 目标实体
     * @param config 粒子配置
     */
    public static void spawnParticles(Entity entity, ParticleConfig config) {
        Level level = entity.level();
        if (!level.isClientSide) {
            return; // 服务端不生成粒子，如需跨端请自行实现网络包
        }

        AABB boundingBox = entity.getBoundingBox();
        Vec3 center = boundingBox.getCenter();
        double halfWidth = (boundingBox.maxX - boundingBox.minX) * 0.5 * config.rangeScale;
        double halfHeight = (boundingBox.maxY - boundingBox.minY) * 0.5 * config.rangeScale;
        double halfDepth = (boundingBox.maxZ - boundingBox.minZ) * 0.5 * config.rangeScale;

        Random random = new Random();

        // 播放声音（可选，只播放一次）
        if (config.soundEvent != null) {
            level.playSound(null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    config.soundEvent,
                    SoundSource.MASTER,
                    config.volume, config.pitch);
        }

        // 生成粒子
        for (int i = 0; i < config.count; i++) {
            // 在缩放后的碰撞箱内随机取点
            double px = center.x + (random.nextDouble() - 0.5) * 2 * halfWidth;
            double py = center.y + (random.nextDouble() - 0.5) * 2 * halfHeight;
            double pz = center.z + (random.nextDouble() - 0.5) * 2 * halfDepth;

            // 获取该粒子的速度
            Vec3 velocity = config.velocityProvider.getVelocity(random, entity);
            level.addParticle(config.particleOptions, px, py, pz,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    // ==================== Builder & Config ====================

    /**
     * 速度提供器函数式接口，用于为每个粒子独立生成速度向量
     */
    @FunctionalInterface
    public interface VelocityProvider {
        Vec3 getVelocity(Random random, Entity entity);
    }

    /**
     * 粒子配置（不可变）
     */
    public static class ParticleConfig {
        final ParticleOptions particleOptions;
        final int count;
        final float rangeScale;
        final VelocityProvider velocityProvider;
        @Nullable final SoundEvent soundEvent;
        final float volume;
        final float pitch;

        private ParticleConfig(ParticleOptions particleOptions,
                               int count,
                               float rangeScale,
                               VelocityProvider velocityProvider,
                               @Nullable SoundEvent soundEvent,
                               float volume,
                               float pitch) {
            this.particleOptions = particleOptions;
            this.count = count;
            this.rangeScale = rangeScale;
            this.velocityProvider = velocityProvider;
            this.soundEvent = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    /**
     * Builder 类，用于构建 ParticleConfig
     */
    public static class Builder {
        private ParticleOptions particleOptions;
        private int count = 10;
        private float rangeScale = 1.0f;
        private VelocityProvider velocityProvider = (rand, entity) -> Vec3.ZERO; // 默认速度为零
        private SoundEvent soundEvent = null;
        private float volume = 1.0f;
        private float pitch = 1.0f;

        private Builder() {}

        /**
         * 设置粒子类型（必填）
         */
        public Builder particle(ParticleOptions particle) {
            this.particleOptions = particle;
            return this;
        }

        /**
         * 设置粒子生成数量
         */
        public Builder count(int count) {
            if (count < 0) throw new IllegalArgumentException("粒子数量不能为负数");
            this.count = count;
            return this;
        }

        /**
         * 设置碰撞箱缩放因子（1.0 = 原始大小，>1 扩大，<1 缩小）
         */
        public Builder rangeScale(float scale) {
            if (scale <= 0) throw new IllegalArgumentException("缩放因子必须 > 0");
            this.rangeScale = scale;
            return this;
        }

        /**
         * 设置固定速度（所有粒子速度相同）
         */
        public Builder velocityFixed(Vec3 velocity) {
            this.velocityProvider = (rand, entity) -> velocity;
            return this;
        }

        /**
         * 设置随机速度范围（每个轴独立均匀随机）
         * @param rangeX X轴速度范围 [-rangeX/2, +rangeX/2]
         * @param rangeY Y轴速度范围
         * @param rangeZ Z轴速度范围
         */
        public Builder velocityRandomRange(double rangeX, double rangeY, double rangeZ) {
            this.velocityProvider = (rand, entity) -> new Vec3(
                    (rand.nextDouble() - 0.5) * rangeX,
                    (rand.nextDouble() - 0.5) * rangeY,
                    (rand.nextDouble() - 0.5) * rangeZ
            );
            return this;
        }

        /**
         * 设置随机球形速度（方向随机，速度大小在 [0, maxSpeed] 之间）
         */
        public Builder velocityRandomSpherical(double maxSpeed) {
            this.velocityProvider = (rand, entity) -> {
                double theta = rand.nextDouble() * 2 * Math.PI;
                double phi = Math.acos(2 * rand.nextDouble() - 1);
                double r = rand.nextDouble() * maxSpeed;
                double x = r * Math.sin(phi) * Math.cos(theta);
                double y = r * Math.sin(phi) * Math.sin(theta);
                double z = r * Math.cos(phi);
                return new Vec3(x, y, z);
            };
            return this;
        }

        /**
         * 使用自定义速度提供器（完全控制每个粒子的速度）
         */
        public Builder velocityCustom(VelocityProvider provider) {

            this.velocityProvider = provider;
            return this;
        }

        /**
         * 设置在生成粒子时播放的声音
         * @param soundEvent 声音事件
         * @param volume     音量 (0.0 - 1.0)
         * @param pitch      音调 (0.5 - 2.0)
         */
        public Builder sound(SoundEvent soundEvent, float volume, float pitch) {
            this.soundEvent = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
            return this;
        }

        /**
         * 构建最终配置对象
         */
        public ParticleConfig build() {
            if (particleOptions == null) {
                throw new IllegalStateException("必须设置粒子类型 (particle)");
            }
            return new ParticleConfig(particleOptions, count, rangeScale,
                    velocityProvider, soundEvent, volume, pitch);
        }
    }


    public static void spawnParticles(Entity entity, ParticleOptions particleTypes,
                                      double density, double speedRange, float rangeScale) {
        Level level = entity.level();
        if (!level.isClientSide && !(level instanceof ServerLevel)) {
            return; // 仅处理客户端或服务端可发送粒子的情况
        }

        AABB bb = entity.getBoundingBox();
        // 应用范围缩放：扩大或缩小碰撞箱
        double halfWidth = (bb.maxX - bb.minX) * 0.5 * rangeScale;
        double halfHeight = (bb.maxY - bb.minY) * 0.5 * rangeScale;
        double halfDepth = (bb.maxZ - bb.minZ) * 0.5 * rangeScale;
        Vec3 center = bb.getCenter();

        double volume = (bb.maxX - bb.minX) * (bb.maxY - bb.minY) * (bb.maxZ - bb.minZ);
        int count = (int) (volume * density) + 5;
        count = Math.min(count, 200);

        Random random = new Random();

        for (int i = 0; i < count; i++) {
            // 在缩放后的范围内随机取点
            double px = center.x + (random.nextDouble() - 0.5) * 2 * halfWidth;
            double py = center.y + (random.nextDouble() - 0.5) * 2 * halfHeight;
            double pz = center.z + (random.nextDouble() - 0.5) * 2 * halfDepth;

            double vx = (random.nextDouble() - 0.5) * speedRange;
            double vy = (random.nextDouble() - 0.5) * speedRange;
            double vz = (random.nextDouble() - 0.5) * speedRange;

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(particleTypes, px, py, pz, 1, vx, vy, vz, 0);
            } else {
                level.addParticle(particleTypes, px, py, pz, vx, vy, vz);
            }
        }
    }

    /**
     * 在实体碰撞箱范围内生成粒子（仅客户端，供实体 tick 中调用）
     *
     * @param entity     目标实体
     * @param particle   粒子类型
     * @param count      每 tick 生成数量上限（受碰撞箱体积限制）
     * @param speedRange 速度随机范围，实际速度在 [-speedRange/2, +speedRange/2]
     * @param rangeScale 碰撞箱缩放因子，1.0 = 原始大小
     */
    public static void spawnParticlesOnClient(Entity entity, ParticleOptions particle,
                                              int count, double speedRange, float rangeScale) {
        Level level = entity.level();
        if (!level.isClientSide) {
            return;
        }
        AABB bb = entity.getBoundingBox();
        Vec3 center = bb.getCenter();
        double hw = (bb.maxX - bb.minX) * 0.5 * rangeScale;
        double hh = (bb.maxY - bb.minY) * 0.5 * rangeScale;
        double hd = (bb.maxZ - bb.minZ) * 0.5 * rangeScale;

        double volume = (bb.maxX - bb.minX) * (bb.maxY - bb.minY) * (bb.maxZ - bb.minZ);
        int n = (int) (volume * count) + 1;
        if (n > 200) n = 200;

        Random random = new Random();
        for (int i = 0; i < n; i++) {
            double px = center.x + (random.nextDouble() - 0.5) * 2 * hw;
            double py = center.y + (random.nextDouble() - 0.5) * 2 * hh;
            double pz = center.z + (random.nextDouble() - 0.5) * 2 * hd;
            double vx = (random.nextDouble() - 0.5) * speedRange;
            double vy = (random.nextDouble() - 0.5) * speedRange;
            double vz = (random.nextDouble() - 0.5) * speedRange;
            level.addParticle(particle, px, py, pz, vx, vy, vz);
        }
    }


    /**
     * 在指定区域内根据完整配置生成粒子（支持客户端/服务端）
     *
     * @param level  世界
     * @param area   轴对齐包围盒
     * @param config 粒子配置（需已设置粒子类型、数量、速度提供器等）
     */
    public static void spawnParticlesInArea(Level level, AABB area, ParticleConfig config) {
        Vec3 center = area.getCenter();
        double halfWidth  = (area.maxX - area.minX) * 0.5 * config.rangeScale;
        double halfHeight = (area.maxY - area.minY) * 0.5 * config.rangeScale;
        double halfDepth  = (area.maxZ - area.minZ) * 0.5 * config.rangeScale;

        Random random = new Random();

        // 播放声音（可选）
        if (config.soundEvent != null && !level.isClientSide) {
            // 服务端播放声音（会同步给附近玩家）
            level.playSound(null, center.x, center.y, center.z,
                    config.soundEvent, SoundSource.MASTER, config.volume, config.pitch);
        } else if (config.soundEvent != null && level.isClientSide) {
            // 客户端直接播放本地声音
            level.playSound(null, center.x, center.y, center.z,
                    config.soundEvent, SoundSource.MASTER, config.volume, config.pitch);
        }

        for (int i = 0; i < config.count; i++) {
            double px = center.x + (random.nextDouble() - 0.5) * 2 * halfWidth;
            double py = center.y + (random.nextDouble() - 0.5) * 2 * halfHeight;
            double pz = center.z + (random.nextDouble() - 0.5) * 2 * halfDepth;
            Vec3 vel = config.velocityProvider.getVelocity(random, null); // 注意：实体参数传null，不影响大多数实现
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(config.particleOptions, px, py, pz, 1, vel.x, vel.y, vel.z, 0);
            } else {
                level.addParticle(config.particleOptions, px, py, pz, vel.x, vel.y, vel.z);
            }
        }
    }
}