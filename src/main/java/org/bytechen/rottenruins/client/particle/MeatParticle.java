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
 *
 * <h3>贴图与注册</h3>
 * <p>贴图是 {@code assets/hall/textures/particle/meat.png} 的<b>单张</b>精灵；
 * 描述文件由 datagen（{@code ParticleData.sprite}）生成到
 * {@code assets/hall/particles/meat.json}；客户端提供器在
 * {@code ClientModEventHandler#onRegisterParticleProviders} 里注册。
 * 生成端见 {@code MeatBurstPacket}（服务端只发 entityId，30 颗肉屑的位置 / 速度
 * 由客户端用 {@code EntityParticleUtils} 现算）。</p>
 *
 * <h3>为什么要用 {@code super.tick()} 而不是照抄参考实现的整段 tick</h3>
 * <p>corruption 的 {@code MeatParticle.tick()} 是把父类 tick 整段重写了一遍
 * （自己做重力、{@code move}、摩擦），目的是绕开原版 {@code onGround} 时
 * 对水平速度乘 0.7 的"地面刹车"，好让肉块在地上多滑一段。hall 这边不这么做：</p>
 * <ul>
 *   <li>原版 tick 已经把重力、{@code move}、碰撞、摩擦、寿命、移除全做对了，
 *       重写一遍等于把这些行为在 1.20.1 上再维护一份，很容易和原版行为漂移；</li>
 *   <li>本类需要的"额外行为"其实只有三件 —— 弹跳、尺寸衰减、贴地额外阻尼，
 *       它们全都能在 {@code super.tick()} <b>之后</b>补算，没必要抢父类的工作。</li>
 * </ul>
 * <p>代价是：{@code friction = 0.99} 会被原版同时乘在 x/y/z 三个轴上
 * （参考实现只对水平轴做空气阻力），竖直下落会因此略微变慢。肉屑本来就只飞一两格，
 * 这点差别看不出来，换来的是"脚踩在原版物理上"的确定性。</p>
 *
 * <h3>弹跳检测：为什么不用参考实现的写法</h3>
 * <p>参考实现是 {@code move()} 之后判断 {@code this.onGround && this.yd < 0}。
 * 这句在 1.20.1 里<strong>靠不住</strong>，原因全在 {@code Particle.move()} 的碰撞实现里：</p>
 * <ul>
 *   <li>撞到方块时，被挡住的水平轴会被<b>直接归零</b>（{@code this.xd = 0} /
 *       {@code this.zd = 0}），竖直轴则<b>不归零</b>，而是打上私有结束标记
 *       {@code stoppedByCollision}；</li>
 *   <li>一旦 {@code stoppedByCollision} 为真（硬着陆必然触发），后续每次
 *       {@code move()} 都直接 return —— 粒子<b>永久冻结</b>。也就是说，即使
 *       {@code onGround && yd < 0} 命中了、把 {@code yd} 改成向上，这个速度也再也不会
 *       被应用：既"永远为假"又"即使为真也没用"，属于一条死分支。</li>
 * </ul>
 * <p>真正的判据是<b>这一 tick 前后的速度差</b>：哪根轴从非 0 变成 0，就说明哪根轴
 * 被方块挡住了。这个写法在本项目的 {@link HeartLoseParticle#tick()} 里已经在用，
 * 这里保持一致（连"从非 0 变 0"必须写成精确比较、不能写成 {@code >= 0} 这个坑都一样）。</p>
 * <p>需要说明的是：1.20.1 的粒子一旦"硬着陆"就会被 {@code stoppedByCollision}
 * 永久冻结，<b>任何</b>粒子都弹不起来 —— 这是原版行为，不是这里少写了什么。
 * 水平两轴的反弹是真实生效的（{@code move()} 会把 xd / zd 归零，且不会触发那个标记）；
 * 竖直分支保留下来，覆盖"擦碰 / 只挡一帧"这类不会触发永久冻结的碰撞。</p>
 */
public class MeatParticle extends TextureSheetParticle {

    /** 存活 tick 数（参考实现同值，120）。 */
    private static final int DEFAULT_LIFETIME = 120;

    /** 重力：比普通碎屑稍大，肉块要有"分量"（参考实现 1F）。 */
    private static final float DEFAULT_GRAVITY = 1.0F;

    /** 基准尺寸：肉块比血点大一截，0.4 格（参考实现同值）。 */
    private static final float DEFAULT_SIZE = 0.4F;

    /**
     * 贴地滑动的额外阻尼（参考实现 {@code GROUND_FRICTION}）。
     *
     * <p>原版 tick 在 {@code onGround} 时已经对水平速度乘过 0.7 了；
     * 这里在"竖直速度几乎为 0（=已经贴住地面）"时再补一个 0.99，
     * 让肉块更快停稳，而不是在血泊里持续打转。</p>
     */
    private static final float GROUND_FRICTION = 0.99F;

    /** 弹跳保留的比例：60% 速度反弹（参考实现同值）。 */
    private static final double BOUNCE_RETAIN = 0.6D;

    /** 最小弹跳速度：低于这个值就直接贴地，避免无限小幅抖动（参考实现同值）。 */
    private static final double MIN_BOUNCE_SPEED = 0.1D;

    private final SpriteSet sprites;

    /** 初始尺寸 —— 尺寸随寿命衰减，所以必须单独留一份基准值。 */
    private final float baseSize;

    protected MeatParticle(ClientLevel level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);

        this.sprites = sprites;
        this.baseSize = DEFAULT_SIZE;

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // 碰撞箱按肉块实际大小给，而不是父类默认的 0.2：
        // 不然"看起来 0.4 格大的肉块"会从三分之一格的缝里漏下去，
        // 或者正好悬在方块边缘上方不落地。
        this.setSize(DEFAULT_SIZE, DEFAULT_SIZE);

        this.lifetime = DEFAULT_LIFETIME;
        this.gravity = DEFAULT_GRAVITY;
        this.friction = 0.99F;

        // 会弹跳、会贴地，所以必须有方块碰撞
        this.hasPhysics = true;

        // 单张精灵：setSpriteFromAge 对只有一张图的 SpriteSet 永远取第 0 张，无副作用。
        // 这里先设一次是为了"出生存活第 0 帧"就能拿到贴图（渲染发生在 tick 之前）。
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public void tick() {
        // 记录 tick 前的三轴速度，用来判断"这一 tick 哪根轴被方块挡住了"（见类注释）
        double prevXd = this.xd;
        double prevYd = this.yd;
        double prevZd = this.zd;

        // 重力 / 移动 / 碰撞 / 摩擦 / 寿命，全部交给原版
        super.tick();

        // ── 弹跳 ──
        // 竖直：踩到地面、tick 前向下运动、且速度被挡停 → 以 60% 速度反弹
        if (this.hasPhysics) {
            if (this.onGround && prevYd < 0.0D && this.yd == 0.0D) {
                this.yd = -prevYd * BOUNCE_RETAIN;
                if (Math.abs(this.yd) < MIN_BOUNCE_SPEED) {
                    // 末速太小：贴地，不再弹（否则会出现永远停不下来的微抖）
                    this.yd = 0.0D;
                } else {
                    // 真弹起来时水平速度略减，模拟"撞地损失能量"
                    this.xd *= 0.98D;
                    this.zd *= 0.98D;
                }
            } else if (prevYd != 0.0D && this.yd == 0.0D) {
                // 撞到天花板之类：只反向，不额外衰减
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

        // ── 尺寸随寿命线性衰减 ──
        // 越旧越小，最后自然缩没（参考实现同款）。age 在 super.tick() 里已经 +1，
        // 所以最后那 tick 的 ageFactor 会是负数 —— clamp 到 0 避免负尺寸，
        // 负的 quadSize 会让四边形顶点顺序翻转（渲染上看起来像被裁掉一块）。
        float ageFactor = 1.0F - (float) this.age / (float) this.lifetime;
        this.quadSize = this.baseSize * Mth.clamp(ageFactor, 0.0F, 1.0F);

        // ── 贴地滑动的额外阻尼 ──
        // 竖直速度"几乎为 0"才算贴住地面；仍在下落时不加，避免把下落中的肉块横向拖住。
        if (this.onGround && Math.abs(this.yd) < 0.01D) {
            this.xd *= GROUND_FRICTION;
            this.zd *= GROUND_FRICTION;
        }

        // 单帧贴图无副作用；保留这行是为了以后 meat.png 换成多帧时只需要改 datagen
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // 贴图带 alpha（肉块边缘不是硬切的），参考实现的 PARTICLE_SHEET_OPAQUE
        // 是 disableBlend() 的不透明批次，alpha 会被硬切成黑边/方角，
        // 所以这里统一用半透明批次 —— 和 hall 其它粒子（heart_lose / ulcerated_meat）一致。
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
