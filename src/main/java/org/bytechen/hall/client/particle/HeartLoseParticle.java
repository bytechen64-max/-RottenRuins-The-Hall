package org.bytechen.hall.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 失心粒子 —— 绯红誓约命中时从受击者身上炸出来的心形碎片。
 *
 * <h3>序列帧，不是随机帧</h3>
 * <p>贴图是 {@code heart_lose0.png} ~ {@code heart_lose4.png} 五张连续帧
 * （一颗心逐渐碎裂/掉落的过程）。所以<b>不能</b>像
 * {@link UlceratedMeatParticle} 那样 {@code pickSprite(sprites)} 随机取一张 ——
 * 那会把动画打乱成一堆互不相干的碎片。</p>
 *
 * <p>这里靠 {@link #setSpriteFromAge(SpriteSet)} 按存活时间推进帧号：
 * 它内部算的是 {@code frame = age * frameCount / lifetime}，所以只要
 * {@link #lifetime} 与帧数匹配，五张图就是<b>依次</b>播完一遍。
 * 帧集的有序性由 datagen 保证
 * （{@code ParticleData.spriteSet(..., 5, true)} 会按前缀枚举出 0..4 并写成动画段）。</p>
 *
 * <h3>运动</h3>
 * <p>速度<b>不在这里生成</b>：方向与模长由服务端算好后经网络包传进来
 * （见 {@code HeartLoseNetwork}），本类只负责原样采用并做轻微上浮，
 * 这样"从攻击者飞向受击者"的方向在服务端与所有客户端上完全一致。</p>
 */
public class HeartLoseParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    /**
     * 碰撞后的速度保留比例。
     *
     * <p>{@code 0.45}：撞一次就弹起将近一半的速度，弹两三次基本停住。
     * 取 1.0 会永远弹不停（能量不衰减），取太小就变成"贴地滑动"看不出弹跳。</p>
     */
    private static final double BOUNCE_RETAIN = 0.45;

    /** 竖直方向撞地后额外再加一点向上的速度，避免末速太小导致"贴地不起"。 */
    private static final double BOUNCE_LIFT = 0.03;

    protected HeartLoseParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        // 序列帧的第一帧起步；之后每 tick 由 setSpriteFromAge 推进
        this.setSpriteFromAge(sprites);

        // 正常重力（和原版"会掉下来的碎屑"一个量级），配合 hasPhysics 撞地会弹起
        this.gravity = 0.9F;
        this.friction = 0.96F;
        // 打开碰撞：Particle.tick 内部会做 move + collideBoundingBox，撞到方块会停住，
        // 再由下面的 tick() 检测"被挡住的轴"并反弹
        this.hasPhysics = true;

        // 帧数 5 → 存活时间给 5 的整数倍附近，正好依次播完一遍多一点。
        // 比原来长一些：现在会落地弹跳，太短的话还没弹起来就消失了
        this.lifetime = 26 + this.random.nextInt(10);

        // 尺寸：0.20~0.30 → 0.12~0.18（整体缩小 40%，即乘 0.6）
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

        // ── 碰撞反弹 ──
        //
        // 原版粒子碰到方块只是"停住"（Particle.tick 里把对应轴的速度<b>置 0</b>），
        // 不会弹。这里靠对比前后速度把它补回来。
        //
        // ★ 竖直轴用原版自己的 {@code onGround} <b>字段</b>（Particle 里是
        //   {@code protected boolean onGround}，不是方法），而不是自己比位置差值：
        //   Particle 内部只在 hasPhysics == true 时更新它
        //   （updateOnGround = this.hasPhysics && !this.stoppedByCollision），
        //   所以只要开了碰撞它就是权威的"这一帧踩到地面了"，比
        //   "速度变成 0 且位置没动"可靠得多 —— 后者在末速极小时会误判。
        //
        // ★ 判定必须写成"从非 0 变 0"，不能写成 ">= 0"：
        //   撞地时原版把 yd 置成 0.0，而 0.0 同时满足 >= 0 与 <= 0，
        //   写成范围判断会让"正常下落"也被当成撞地（或反过来漏判）。
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
