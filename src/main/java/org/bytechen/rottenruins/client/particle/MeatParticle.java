package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 肉块粒子 —— 爆体 / 尸块碎裂时炸出来的肉屑：大、重、会弹跳，随寿命慢慢缩没。
 * <p>贴图是 {@code assets/hall/textures/particle/meat.png} 单张精灵（datagen 生成描述文件），生成端见
 * {@code MeatBurstPacket}：服务端只发 entityId，肉屑位置 / 速度由客户端现算。
 * <p>用 {@code super.tick()} 而非照抄参考实现整段重写：原版已把重力、move、碰撞、摩擦、寿命做对，弹跳 /
 * 尺寸衰减 / 贴地阻尼都能在其后补算（代价是 {@code friction} 三轴同乘，下落略慢）。
 * <p>弹跳判据用「这一 tick 前后的速度差」而非 {@code onGround && yd < 0}：{@code Particle.move()} 撞到
 * 方块时把水平轴归零、并给竖直轴打上 {@code stoppedByCollision}（此后永久冻结），那条分支是死路。
 */
public class MeatParticle extends TextureSheetParticle {

    private static final int DEFAULT_LIFETIME = 120;

    private static final float DEFAULT_GRAVITY = 1.0F;

    private static final float DEFAULT_SIZE = 0.4F;

    /** 贴地滑动的额外阻尼：原版在 {@code onGround} 已乘 0.7，这里再补 0.99 让它更快停稳。 */
    private static final float GROUND_FRICTION = 0.99F;

    private static final double BOUNCE_RETAIN = 0.6D;

    private static final double MIN_BOUNCE_SPEED = 0.1D;

    private final SpriteSet sprites;

    private final float baseSize;

    protected MeatParticle(ClientLevel level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);

        this.sprites = sprites;
        this.baseSize = DEFAULT_SIZE;

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // 碰撞箱按肉块实际大小给（父类默认 0.2）：否则大肉块会从缝里漏下去或悬在边缘不落地
        this.setSize(DEFAULT_SIZE, DEFAULT_SIZE);

        this.lifetime = DEFAULT_LIFETIME;
        this.gravity = DEFAULT_GRAVITY;
        this.friction = 0.99F;

        this.hasPhysics = true;

        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public void tick() {
        // 记录 tick 前的三轴速度，用来判断这一 tick 哪根轴被方块挡住了（见类注释）
        double prevXd = this.xd;
        double prevYd = this.yd;
        double prevZd = this.zd;

        super.tick();

        // 竖直：踩到地面、tick 前向下、且速度被挡停 → 以 60% 速度反弹
        if (this.hasPhysics) {
            if (this.onGround && prevYd < 0.0D && this.yd == 0.0D) {
                this.yd = -prevYd * BOUNCE_RETAIN;
                if (Math.abs(this.yd) < MIN_BOUNCE_SPEED) {
                    this.yd = 0.0D;
                } else {
                    this.xd *= 0.98D;
                    this.zd *= 0.98D;
                }
            } else if (prevYd != 0.0D && this.yd == 0.0D) {
                this.yd = -prevYd * BOUNCE_RETAIN;
            }

            // 水平两轴：撞墙时 move() 会把该轴速度直接归零，这里按同比例弹回
            if (prevXd != 0.0D && this.xd == 0.0D) {
                this.xd = -prevXd * BOUNCE_RETAIN;
            }
            if (prevZd != 0.0D && this.zd == 0.0D) {
                this.zd = -prevZd * BOUNCE_RETAIN;
            }
        }

        // age 在 super.tick() 里已 +1，最后一 tick 的 ageFactor 会是负数：负的 quadSize 会让
        // 四边形顶点顺序翻转（看起来像被裁掉一块），所以 clamp 到 0。
        float ageFactor = 1.0F - (float) this.age / (float) this.lifetime;
        this.quadSize = this.baseSize * Mth.clamp(ageFactor, 0.0F, 1.0F);

        // 竖直速度几乎为 0 才算贴住地面；仍在下落时不加，避免把下落中的肉块横向拖住
        if (this.onGround && Math.abs(this.yd) < 0.01D) {
            this.xd *= GROUND_FRICTION;
            this.zd *= GROUND_FRICTION;
        }

        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // 贴图带 alpha：参考实现的 PARTICLE_SHEET_OPAQUE 会把 alpha 硬切成黑边/方角，故用半透明批次
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 客户端提供器：{@code RegisterParticleProvidersEvent#registerSpriteSet} */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public MeatParticle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double vx, double vy, double vz) {
            return new MeatParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
