package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 血滴粒子：有方块碰撞、会弹跳，落地"滴答"一声后继续滚跳，存活 6 秒后缩小淡出。
 * 移动是整个接管的（见 {@link #move}）：父类 {@code Particle#move} 撞到竖直面会置私有
 * {@code stoppedByCollision} 把粒子永久冻结，所以不能用 hasPhysics + 父类 move 拿到弹跳。
 */
public class BloodParticle extends TextureSheetParticle {

    private static final int DEFAULT_LIFETIME = 120;

    private static final float DEFAULT_GRAVITY = 1.0F;

    private static final float DEFAULT_FRICTION = 0.97F;

    private static final float BASE_QUAD_SIZE = 0.16F;

    private static final float QUAD_SIZE_RANDOM = 0.08F;

    private static final int FADE_OUT_TICKS = 20;

    /** 撞方块后该轴保留的速度比例（0.6 → 弹一下能明显看到）。 */
    private static final double BOUNCE_RETAIN = 0.6D;

    private static final double MIN_BOUNCE_SPEED = 0.06D;

    private static final double GROUND_HORIZONTAL_DAMPING = 0.7D;

    /** 第一次贴地时播放滴答声的概率（防止一簇血滴同时落地时音效糊成一片）。 */
    private static final float LANDING_SOUND_CHANCE = 0.4F;

    private boolean landingSoundHandled = false;

    protected BloodParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);

        this.lifetime = DEFAULT_LIFETIME;
        this.gravity = DEFAULT_GRAVITY;
        this.friction = DEFAULT_FRICTION;
        this.quadSize = BASE_QUAD_SIZE + this.random.nextFloat() * QUAD_SIZE_RANDOM;

        // hasPhysics 只是语义标记：真正的碰撞在本类 move() 里做，父类会写入私有冻结标记
        this.hasPhysics = true;
        this.onGround = false;
    }

    /** 供生成方覆盖存活时间；淡出段按 {@code lifetime} 现算（见 {@link #getQuadSize(float)}），改长度不会算错。 */
    public BloodParticle setLiftTime(int maxTime) {
        this.lifetime = maxTime;
        return this;
    }

    /**
     * 移动 + 方块碰撞 + 逐轴弹跳：用 {@link Entity#collideBoundingBox} 把位移裁剪到碰撞边界，
     * 被挡住的轴反向而非归零，且不触碰那个会冻结粒子的私有标记。重力/摩擦/寿命仍由父类 tick() 负责。
     */
    @Override
    public void move(double dx, double dy, double dz) {
        if (dx == 0.0D && dy == 0.0D && dz == 0.0D) return;

        Vec3 motion = new Vec3(dx, dy, dz);
        Vec3 clipped = Entity.collideBoundingBox(null, motion, this.getBoundingBox(), this.level, List.of());

        boolean blockedX = clipped.x != dx;
        boolean blockedY = clipped.y != dy;
        boolean blockedZ = clipped.z != dz;

        // 先按裁剪后的位移真正移动（不调父类 move，避免 stoppedByCollision 永久冻结）
        if (clipped.x != 0.0D || clipped.y != 0.0D || clipped.z != 0.0D) {
            this.setBoundingBox(this.getBoundingBox().move(clipped.x, clipped.y, clipped.z));
            this.setLocationFromBoundingbox();
        }

        this.onGround = blockedY && dy < 0.0D;

        if (blockedX) this.xd = -this.xd * BOUNCE_RETAIN;
        if (blockedZ) this.zd = -this.zd * BOUNCE_RETAIN;
        if (blockedY) {
            this.yd = -this.yd * BOUNCE_RETAIN;
            if (Math.abs(this.yd) < MIN_BOUNCE_SPEED) {
                // 末速太小：贴地，不再弹（否则会出现永远停不下来的微抖）
                this.yd = 0.0D;
            }
            if (this.onGround) {
                // 落地：水平方向额外衰减，让血滴很快滚停；顺便处理一次落地音效
                this.xd *= GROUND_HORIZONTAL_DAMPING;
                this.zd *= GROUND_HORIZONTAL_DAMPING;
                this.playLandingSoundOnce();
            }
        }
    }

    /** 只在第一次贴地时尝试播放：弹跳会反复触地，每 tick 一个音效会变成噪音；音量 0.2~0.6 随机。 */
    private void playLandingSoundOnce() {
        if (this.landingSoundHandled) return;
        this.landingSoundHandled = true;

        if (this.random.nextFloat() > LANDING_SOUND_CHANCE) return;

        float volume = Mth.randomBetween(this.random, 0.2F, 0.6F);
        SoundEvent drip = SoundEvents.BEEHIVE_DRIP;
        this.level.playLocalSound(this.x, this.y, this.z, drip,
                SoundSource.BLOCKS, volume, 1.0F, false);
    }

    /**
     * 淡出只缩尺寸、不加透明度淡入（出生即 alpha=1）。在 {@code getQuadSize} 里赋值正好赶在本帧绘制之前，
     * 缩小动画能拿到帧间插值，不会在 tick 边界"跳"。
     */
    @Override
    public float getQuadSize(float partialTick) {
        float time = (float) this.age + partialTick;
        float size = this.quadSize;

        this.alpha = 1.0F;

        float fadeOutStart = (float) (this.lifetime + 1 - FADE_OUT_TICKS);
        if (time > fadeOutStart) {
            float t = (time - fadeOutStart) / (float) FADE_OUT_TICKS;
            size *= Mth.clamp(1.0F - t, 0.0F, 1.0F);
        }

        return size;
    }

    @Override
    public ParticleRenderType getRenderType() {
        // 贴图带 alpha（血点边缘是渐隐的），必须走半透明批次，不能用 OPAQUE
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public BloodParticle createParticle(SimpleParticleType type, ClientLevel level,
                                            double x, double y, double z,
                                            double vx, double vy, double vz) {
            BloodParticle particle = new BloodParticle(level, x, y, z);
            // 单张精灵也走 pickSprite：以后把 blood.png 换成序列帧不用改这里
            particle.pickSprite(this.sprites);
            // 速度由调用方（受击血花包）决定：方向 = 攻击源 → 受击者，模长按距离
            particle.setParticleSpeed(vx, vy, vz);
            return particle;
        }
    }
}
