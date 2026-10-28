package org.bytechen.hall.client.particle;

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
 * 血滴粒子 —— 受击 / 命中瞬间溅出来的血点：<b>有方块碰撞、会弹跳</b>，落地"滴答"一声后继续滚跳，
 * 活得够久（默认 6 秒），最后缩小淡出。
 *
 * <h3>贴图与注册</h3>
 * <p>贴图是 {@code assets/hall/textures/particle/blood.png} 的<b>单张</b>精灵
 * （不是序列帧，所以 {@code pickSprite} 取到的永远是同一张）；
 * 粒子描述由 datagen（{@code ParticleData.sprite}）生成到
 * {@code assets/hall/particles/blood.json}；客户端提供器在
 * {@code ClientModEventHandler#onRegisterParticleProviders} 里注册。
 * 生成端见 {@link org.bytechen.hall.network.s2c.BloodBurstPacket}
 * （受击血花：服务端按攻击源方向与距离算好速度）。</p>
 *
 * <h3>为什么自己重写 {@link #move(double, double, double)} 而不是用 {@code hasPhysics} + 父类 move</h3>
 * <p>1.20.1 的 {@code Particle#move} <b>没有</b>真正的"弹跳"能力：撞到竖直方向时它不会把
 * {@code yd} 归零，而是把私有的 {@code stoppedByCollision} 置真，之后每次 {@code move}
 * 直接 return —— 粒子被<b>永久冻结</b>在落点。也就是说，只要走父类 move，任何竖直弹跳都
 * 不可能被应用（这也是 corruption 的 {@code MeatParticle} 里那条 {@code onGround && yd < 0}
 * 分支为什么是死代码的原因）。</p>
 * <p>解决办法是<b>整个接管移动</b>：在 {@link #move} 里直接调
 * {@link Entity#collideBoundingBox}（父类内部用的也是它）把本 tick 的位移裁剪到方块边界，
 * 再按"哪根轴被挡住"分别反弹。这样既保留了原版碰撞语义（贴墙、贴地、卡角落），
 * 又完全绕开了那个私有冻结标记。基类 {@code tick()} 仍然负责重力、摩擦、寿命与移除，
 * 所以这里只覆盖移动这一件事。</p>
 *
 * <h3>尺寸 / 寿命 / 手感</h3>
 * <ul>
 *   <li><b>尺寸</b>：0.16~0.24 格随机（旧版 0.1 太小，远处几乎看不见）；</li>
 *   <li><b>寿命</b>：120 tick（6 秒）—— 之前 60 tick 还在空中就没了；</li>
 *   <li><b>重力 / 阻力</b>：gravity 1.0 / friction 0.97（旧版 1.5 / 0.95）——
 *       落得慢一点、飞得远一点，配合 {@code BloodBurstPacket} 里更大的初速，
 *       血能溅出数格而不是贴在脚边；</li>
 *   <li><b>弹跳</b>：撞地保留 60% 竖直速度，水平按 70% 衰减，末速低于 0.06 直接贴地；
 *       撞墙 / 撞天花板同样按 60% 反弹；</li>
 *   <li><b>淡出</b>：最后 20 tick 缩小消失（旧版 10 tick 太急）；<b>不做透明度淡入</b> ——
 *       血滴出生即 alpha=1，避免刚喷出来时半透明发虚。</li>
 * </ul>
 *
 * <h3>落地音效</h3>
 * <p>每颗粒子<b>只在第一次贴地时</b>尝试播放 {@link SoundEvents#BEEHIVE_DRIP}
 * （随机 40% 概率，音量 0.2~0.6）—— 弹跳会让粒子反复触地，不做闸门会变成连续的"哒哒哒"。
 * 音效是客户端本地声音（{@code playLocalSound}），不需要同步。</p>
 */
public class BloodParticle extends TextureSheetParticle {

    /** 存活 tick 数：6 秒（旧版 3 秒，空中就消失了）。 */
    private static final int DEFAULT_LIFETIME = 120;

    /** 重力：血滴仍比碎屑沉，但比旧版 1.5 轻一档，飞得更远。 */
    private static final float DEFAULT_GRAVITY = 1.0F;

    /** 空气阻力：0.97（旧版 0.95），衰减慢一点，溅射距离更远。 */
    private static final float DEFAULT_FRICTION = 0.97F;

    /** 基准尺寸（旧版 0.1 太小）；实际尺寸再叠 {@link #QUAD_SIZE_RANDOM} 的随机。 */
    private static final float BASE_QUAD_SIZE = 0.16F;

    /** 尺寸随机幅度：最终 0.16 ~ 0.24 格。 */
    private static final float QUAD_SIZE_RANDOM = 0.08F;

    /** 淡出（缩小）持续 tick 数：旧版 10，现在拉长到 20，避免"啪一下没了"。 */
    private static final int FADE_OUT_TICKS = 20;

    /** 撞方块后该轴保留的速度比例（0.6 → 弹一下能明显看到）。 */
    private static final double BOUNCE_RETAIN = 0.6D;

    /** 竖直反弹末速低于此值时直接贴地，避免地面上的无限微抖。 */
    private static final double MIN_BOUNCE_SPEED = 0.06D;

    /** 每次撞地后水平速度的额外衰减 —— 模拟血滴落地时的能量损失。 */
    private static final double GROUND_HORIZONTAL_DAMPING = 0.7D;

    /** 第一次贴地时播放滴答声的概率（防止一簇血滴同时落地时音效糊成一片）。 */
    private static final float LANDING_SOUND_CHANCE = 0.4F;

    /** 是否已经处理过"第一次落地音效"（无论最后是否真的播放）。 */
    private boolean landingSoundHandled = false;

    protected BloodParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);

        this.lifetime = DEFAULT_LIFETIME;
        this.gravity = DEFAULT_GRAVITY;
        this.friction = DEFAULT_FRICTION;
        this.quadSize = BASE_QUAD_SIZE + this.random.nextFloat() * QUAD_SIZE_RANDOM;

        // 语义标记：本粒子有方块碰撞。真正的碰撞在本类的 move() 里做 ——
        // 父类 move 的碰撞会写入私有 stoppedByCollision 导致永久冻结（见类注释）。
        this.hasPhysics = true;
        this.onGround = false;
    }

    /**
     * 供生成方覆盖存活时间（保留的链式 API）。
     *
     * <p>淡出段位置按 {@code lifetime} 现算（{@link #getQuadSize(float)}），
     * 所以改长度不会把淡出算错。</p>
     */
    public BloodParticle setLiftTime(int maxTime) {
        this.lifetime = maxTime;
        return this;
    }

    /**
     * 移动 + 方块碰撞 + 逐轴弹跳。
     *
     * <p>流程与父类 {@code Particle#move} 一致：用 {@link Entity#collideBoundingBox}
     * 把位移裁剪到碰撞边界，再更新碰撞箱/坐标；区别在于<b>被挡住的轴不归零、而是反弹</b>，
     * 并且不触碰那个会冻结粒子的私有标记。</p>
     *
     * <p>为什么用同一次裁剪结果逐轴判断"有没有被挡"：{@code collideBoundingBox} 返回的是
     * 三轴分别裁剪后的位移，某轴分量与原位移不同就说明该轴方向被方块挡住了 ——
     * 这与原版 {@code Entity#move} 的判定方式相同，不依赖任何"速度归零"的副作用。</p>
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

        // 自己维护"这一 tick 是否踩在地面上"（向下运动且竖直被挡）
        this.onGround = blockedY && dy < 0.0D;

        // ── 弹跳：被挡住的轴把速度反向并保留一部分 ──
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

    /**
     * 第一次贴地时尝试播放滴答声。
     *
     * <p>闸门是 {@link #landingSoundHandled} 而不是"每次 onGround"：弹跳会让粒子反复触地，
     * 每 tick 一个音效会变成噪音。一次喷血里具体哪几颗出声由随机概率决定，
     * 听感上是零星的"啪嗒"而不是一片爆响。</p>
     */
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
     * 淡出（只缩尺寸，不改 alpha）。
     *
     * <p>原版 {@code SingleQuadParticle#render} 会先调 {@code getQuadSize(partialTick)}
     * 再写顶点颜色，所以在这里赋值正好赶在本帧绘制之前，缩小动画能拿到帧间插值，
     * 不会在 20Hz 的 tick 边界上"跳"。</p>
     *
     * <p><b>不做透明度淡入</b>：出生即 {@code alpha = 1}，血滴刚喷出来就是实的，
     * 不会有一瞬间半透明发虚；消失阶段也只缩尺寸、不降 alpha，
     * 看起来是"越缩越小直到没有"。</p>
     */
    @Override
    public float getQuadSize(float partialTick) {
        float time = (float) this.age + partialTick;
        float size = this.quadSize;

        // 出生即完全不透明（不做淡入）
        this.alpha = 1.0F;

        // 淡出段起点：lifetime + 1 - 淡出长度（与旧版同一公式）
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

    /** 客户端提供器：{@code RegisterParticleProvidersEvent#registerSpriteSet} */
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
            // 单张精灵也走 pickSprite：SpriteSet 只有一张时它永远返回那一张，
            // 这样以后把 blood.png 换成序列帧也不用改这里（换的只是注释和 datagen）。
            particle.pickSprite(this.sprites);
            // 速度由调用方（受击血花包）决定：方向 = 攻击源 → 受击者，模长按距离。
            particle.setParticleSpeed(vx, vy, vz);
            return particle;
        }
    }
}
