package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 失心粒子：绯红誓约命中时炸出的心形碎片。贴图是 5 张连续帧，必须靠 {@link #setSpriteFromAge}
 * 按 age 推进而不能 {@code pickSprite} 随机取，否则动画会打乱；帧序由 datagen 的 spriteSet 保证。
 * 速度不在客户端生成：方向与模长由服务端算好经网络包传入，保证各端一致。
 */
public class HeartLoseParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    /** 碰撞后的速度保留比例：0.45 撞两三次基本停住，1.0 会永远弹不停，太小则看不出弹跳。 */
    private static final double BOUNCE_RETAIN = 0.45;

    /** 竖直撞地后额外补一点向上速度，避免末速太小导致"贴地不起"。 */
    private static final double BOUNCE_LIFT = 0.03;

    protected HeartLoseParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        // 序列帧从第一帧起步，之后每 tick 由 setSpriteFromAge 按 age 推进
        this.setSpriteFromAge(sprites);

        // 正常重力（与原版碎屑同量级），配合 hasPhysics 撞地会弹起
        this.gravity = 0.9F;
        this.friction = 0.96F;
        // 必须开碰撞：tick() 靠它检测"哪个轴被方块挡住"并反弹
        this.hasPhysics = true;

        // 存活时间取帧数（5）的整数倍附近，正好依次播完一遍多一点；太短还没弹起来就消失了
        this.lifetime = 26 + this.random.nextInt(10);

        this.quadSize = 0.12F + this.random.nextFloat() * 0.06F;
        this.alpha = 1.0F;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    @Override
    public void tick() {
        // 记录本 tick 的速度，用来判断"哪个轴被方块挡住了"
        double prevXd = this.xd;
        double prevYd = this.yd;
        double prevZd = this.zd;

        super.tick();
        // 按存活时间推进到的帧号 —— 顺序播放的关键
        this.setSpriteFromAge(this.sprites);

        // 撞地反弹：原版 Particle.tick 只会把被挡轴的速度置 0，这里对比前后速度补回来。
        // 竖直轴用 Particle 自己的 onGround 字段（只在 hasPhysics 时更新，是权威的踩地信号），
        // 且判定必须写成"非 0 → 0"：0.0 同时满足 >= 0 与 <= 0，范围判断会误判正常下落。
        if (this.hasPhysics) {
            boolean hitGround = this.onGround && prevYd < 0.0 && this.yd == 0.0;
            if (hitGround) {
                // 向下撞地 → 弹起；额外给一点 LIFT，避免末速太小导致"贴地不起"
                this.yd = -prevYd * BOUNCE_RETAIN + BOUNCE_LIFT;
            } else if (prevYd != 0.0 && this.yd == 0.0) {
                // 撞到天花板之类：只反向，不加抬升
                this.yd = -prevYd * BOUNCE_RETAIN;
            }
            if (prevXd != 0.0 && this.xd == 0.0) {
                this.xd = -prevXd * BOUNCE_RETAIN;
            }
            if (prevZd != 0.0 && this.zd == 0.0) {
                this.zd = -prevZd * BOUNCE_RETAIN;
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 客户端提供器：{@code RegisterParticleProvidersEvent#registerSpriteSet} */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public HeartLoseParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                double x, double y, double z,
                                                double vx, double vy, double vz) {
            return new HeartLoseParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
