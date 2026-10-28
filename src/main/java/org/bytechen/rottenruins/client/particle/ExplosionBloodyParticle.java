package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

/**
 * 血腥爆炸粒子 —— 爆体瞬间在原地炸开的一团血雾，16 帧序列动画。
 *
 * <h3>贴图与注册</h3>
 * <p>贴图是 16 张序列帧：
 * {@code assets/hall/textures/particle/explosion_bloody0.png} ~ {@code explosion_bloody15.png}
 * （文件名<b>不带下划线</b>）；有序帧列表由
 * {@link RegisterParticles#explosionBloodyFrames()} 给出，datagen
 * （{@code ParticleData.spriteSet}）把它写成有序数组
 * {@code assets/hall/particles/explosion_bloody.json}；客户端提供器在
 * {@code ClientModEventHandler#onRegisterParticleProviders} 里注册。</p>
 *
 * <h3>为什么 {@code lifetime} 必须等于帧数</h3>
 * <p>1.20.1 的 {@code SpriteSet#get(age, lifetime)} 实现在
 * {@code ParticleEngine.MutableSpriteSet} 里，公式是
 * {@code index = age * (frameCount - 1) / lifetime}（整数除法）。它<b>不是</b>按
 * "每帧停留几个 tick"来算的，所以：</p>
 * <ul>
 *   <li>{@code lifetime < frameCount}（参考实现是 10 &lt; 16）→ 每 tick 跨过一格以上，
 *       中间帧播不到，动画看起来是"卡帧快进"；</li>
 *   <li>{@code lifetime == frameCount} → age 每 +1 帧号恰好 +1，按 0→14 连续播完
 *       （第 0 帧在 age=0 与 age=1 各出现一次，多停 1 tick），不跳帧。</li>
 * </ul>
 * <p><b>1.20.1 的固有边界（别当成这里写错了）：</b>公式里没有 clamp，下标就是
 * {@code age * (frameCount - 1) / lifetime} 的<em>整数除法</em>；而活着的粒子恒有
 * {@code age < lifetime}，所以能取到的最大下标只有 {@code frameCount - 2} —— 也就是
 * {@code explosion_bloody15} 这张收尾帧在正常寿命里永远取不到。这是原版
 * {@code SpriteSet} API 的行为（参考实现同样吃这个亏，而且它 {@code lifetime = 10}
 * 还会跳帧）；第 15 帧本来只是几乎全透明的一小道残迹，视觉上无影响。把
 * {@code lifetime} 取成帧数已经做到"其余 15 帧连续、不跳帧"，是这套 API 下能做到的上限。</p>
 * <p>所以 {@code lifetime} 直接取 {@link RegisterParticles#EXPLOSION_BLOODY_FRAMES}，
 * 帧数和寿命只维护一个常量：改帧数时 datagen 的帧列表与客户端动画会一起变。</p>
 *
 * <h3>视觉参数</h3>
 * <ul>
 *   <li>{@code gravity = 0}、初速度强制为 0：爆炸是"钉在原地扩散"的，不该飘走、也不该掉；</li>
 *   <li>{@code quadSize = 1.4}：整团血雾大约两格半见方，比实体本身大一圈；</li>
 *   <li>{@link #getLightColor(float)} 固定返回 {@code 0xF000F0}（天空光 / 方块光都是 15）：
 *       爆炸本身是发光事件，在洞穴 / 夜里也该是亮的，不能因为所处方块暗就变黑；</li>
 *   <li>渲染批次用 {@code PARTICLE_SHEET_TRANSLUCENT}：贴图带 alpha
 *       （爆炸边缘是渐隐的），而参考实现用的 {@code PARTICLE_SHEET_LIT} 在 1.20.1 里
 *       是 {@code disableBlend()} 的不透明批次，alpha 边缘会被硬切成黑边；
 *       何况光照已经固定成全亮，LIT 唯一的光照贴图能力也用不上。</li>
 * </ul>
 */
public class ExplosionBloodyParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected ExplosionBloodyParticle(ClientLevel level, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed,
                                      SpriteSet sprites) {
        super(level, x, y, z);

        this.sprites = sprites;

        // 不随重力下落，也不采用调用方传进来的速度（爆炸固定发生在实体原来的位置）
        this.gravity = 0.0F;
        this.friction = 0.9F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;

        this.quadSize = 1.4F;

        // 帧数 == 寿命，理由见类注释
        this.lifetime = RegisterParticles.EXPLOSION_BLOODY_FRAMES;

        // 出生存活第 0 帧就要有贴图（渲染可能发生在第一次 tick 之前）
        this.setSpriteFromAge(this.sprites);
    }

    /**
     * 固定全亮。
     *
     * <p>{@code 0xF000F0} = 天空光 15（{@code 15 << 20}）| 方块光 15（{@code 15 << 4}）。
     * 不这么做的话，夜里或者洞穴里炸开的血雾会几乎看不见 —— 而"爆体"本身是个
     * 明亮的事件，玩家应该一眼看到。</p>
     */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        // 按 age 推进帧号（setSpriteFromAge 内部就是 sprites.get(age, lifetime)）
        this.setSpriteFromAge(this.sprites);
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
        public ExplosionBloodyParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                      double x, double y, double z,
                                                      double vx, double vy, double vz) {
            return new ExplosionBloodyParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
