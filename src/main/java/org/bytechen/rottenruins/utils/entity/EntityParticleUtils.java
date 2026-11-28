package org.bytechen.rottenruins.utils.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * 实体粒子生成工具：在实体碰撞箱范围内随机播撒粒子，支持速度策略、范围缩放与伴随音效。
 * 用法：{@code EntityParticleUtils.builder().particle(...).count(50).rangeScale(1.5f).build()}，
 * 再调 {@code spawnParticles(entity, config)}。
 */
public final class EntityParticleUtils {

    private EntityParticleUtils() {}

    /** 开始构建粒子配置。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 按配置为实体生成粒子（仅客户端生效）；rangeScale 缩放碰撞箱，音效只播一次。 */
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

        if (config.soundEvent != null) {
            level.playSound(null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    config.soundEvent,
                    SoundSource.MASTER,
                    config.volume, config.pitch);
        }

        for (int i = 0; i < config.count; i++) {
            double px = center.x + (random.nextDouble() - 0.5) * 2 * halfWidth;
            double py = center.y + (random.nextDouble() - 0.5) * 2 * halfHeight;
            double pz = center.z + (random.nextDouble() - 0.5) * 2 * halfDepth;

            Vec3 velocity = config.velocityProvider.getVelocity(random, entity);
            level.addParticle(config.particleOptions, px, py, pz,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    /** 速度提供器：为每颗粒子独立生成初速。 */
    @FunctionalInterface
    public interface VelocityProvider {
        Vec3 getVelocity(Random random, Entity entity);
    }

    /** 粒子配置（不可变）。 */
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

    /** Builder 类，用于构建 ParticleConfig。 */
    public static class Builder {
        private ParticleOptions particleOptions;
        private int count = 10;
        private float rangeScale = 1.0f;
        private VelocityProvider velocityProvider = (rand, entity) -> Vec3.ZERO; // 默认速度为零
        private SoundEvent soundEvent = null;
        private float volume = 1.0f;
        private float pitch = 1.0f;

        private Builder() {}

        /** 粒子类型（必填）。 */
        public Builder particle(ParticleOptions particle) {
            this.particleOptions = particle;
            return this;
        }

        /** 生成数量；负数抛 IllegalArgumentException。 */
        public Builder count(int count) {
            if (count < 0) throw new IllegalArgumentException("粒子数量不能为负数");
            this.count = count;
            return this;
        }

        /** 碰撞箱缩放因子：1.0 = 原始大小，>1 扩大，<1 缩小。 */
        public Builder rangeScale(float scale) {
            if (scale <= 0) throw new IllegalArgumentException("缩放因子必须 > 0");
            this.rangeScale = scale;
            return this;
        }

        /** 所有粒子用同一个固定初速。 */
        public Builder velocityFixed(Vec3 velocity) {
            this.velocityProvider = (rand, entity) -> velocity;
            return this;
        }

        /** 每轴独立均匀随机初速，实际范围 [-range/2, +range/2]。 */
        public Builder velocityRandomRange(double rangeX, double rangeY, double rangeZ) {
            this.velocityProvider = (rand, entity) -> new Vec3(
                    (rand.nextDouble() - 0.5) * rangeX,
                    (rand.nextDouble() - 0.5) * rangeY,
                    (rand.nextDouble() - 0.5) * rangeZ
            );
            return this;
        }

        /** 方向随机的球形初速，速率在 [0, maxSpeed]。 */
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

        /** 自定义速度提供器（完全控制每颗粒子的初速，entity 参数可能为 null）。 */
        public Builder velocityCustom(VelocityProvider provider) {

            this.velocityProvider = provider;
            return this;
        }

        /** 生成粒子时同时播放的音效（volume 0.0~1.0、pitch 0.5~2.0）。 */
        public Builder sound(SoundEvent soundEvent, float volume, float pitch) {
            this.soundEvent = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
            return this;
        }

        /** 构建配置；未设置粒子类型时抛 IllegalStateException。 */
        public ParticleConfig build() {
            if (particleOptions == null) {
                throw new IllegalStateException("必须设置粒子类型 (particle)");
            }
            return new ParticleConfig(particleOptions, count, rangeScale,
                    velocityProvider, soundEvent, volume, pitch);
        }
    }

    /** 按碰撞箱体积 × density（+5，上限 200）播撒粒子；服务端走 sendParticles 广播给附近玩家。 */
    public static void spawnParticles(Entity entity, ParticleOptions particleTypes,
                                      double density, double speedRange, float rangeScale) {
        Level level = entity.level();
        if (!level.isClientSide && !(level instanceof ServerLevel)) {
            return; // 仅处理客户端或服务端可发送粒子的情况
        }

        AABB bb = entity.getBoundingBox();
        double halfWidth = (bb.maxX - bb.minX) * 0.5 * rangeScale;
        double halfHeight = (bb.maxY - bb.minY) * 0.5 * rangeScale;
        double halfDepth = (bb.maxZ - bb.minZ) * 0.5 * rangeScale;
        Vec3 center = bb.getCenter();

        double volume = (bb.maxX - bb.minX) * (bb.maxY - bb.minY) * (bb.maxZ - bb.minZ);
        int count = (int) (volume * density) + 5;
        count = Math.min(count, 200);

        Random random = new Random();

        for (int i = 0; i < count; i++) {
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

    /** 爆体：按 density 1.0、速度扰动 0.1 炸开血腥爆炸粒子（数量随体积，5~200 颗）。 */
    public static void spawnExplosionParticles(Entity entity) {
        spawnParticles(entity, RegisterParticles.EXPLOSION_BLOODY.get(), 1.0D, 0.1D, 1.0F);
    }

    /** 仅客户端：以 count 作密度在碰撞箱内播撒粒子（至少 1 颗、上限 200），供实体 tick 调用。 */
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

    /** 在给定 AABB 区域内按配置撒粒子（客户端 addParticle / 服务端 sendParticles 均可用）。 */
    public static void spawnParticlesInArea(Level level, AABB area, ParticleConfig config) {
        Vec3 center = area.getCenter();
        double halfWidth  = (area.maxX - area.minX) * 0.5 * config.rangeScale;
        double halfHeight = (area.maxY - area.minY) * 0.5 * config.rangeScale;
        double halfDepth  = (area.maxZ - area.minZ) * 0.5 * config.rangeScale;

        Random random = new Random();

        if (config.soundEvent != null && !level.isClientSide) {
            level.playSound(null, center.x, center.y, center.z,
                    config.soundEvent, SoundSource.MASTER, config.volume, config.pitch);
        } else if (config.soundEvent != null && level.isClientSide) {
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