package org.bytechen.rottenruins.overworld.registry;

import org.bytechen.rottenruins.HallMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegisterParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HallMod.MODID);

    // Your particles here
    // public static final RegistryObject<SimpleParticleType> EXAMPLE_PARTICLE =
    //         PARTICLE_TYPES.register("example_particle", () -> new SimpleParticleType(false));

    /**
     * 溃烂肉屑 —— 溃烂系生物（斥候 / 巨碑）身上周期性掉落的血肉粒子。
     * <p>
     * 贴图：{@code assets/hall/textures/particle/ulcerated_meat.png}；
     * 粒子描述由 datagen（{@code ParticleData}）生成到
     * {@code assets/hall/particles/ulcerated_meat.json}；
     * 客户端提供器在 {@code ClientModEventHandler#onRegisterParticleProviders} 里注册。
     */
    public static final RegistryObject<SimpleParticleType> ULCERATED_MEAT =
            PARTICLE_TYPES.register("ulcerated_meat", () -> new SimpleParticleType(false));

    /**
     * 失心粒子 —— 绯红誓约命中时被打出来的心形碎片。
     *
     * <p>贴图是 5 张序列帧：
     * {@code assets/hall/textures/particle/heart_lose0.png} ~ {@code heart_lose4.png}
     * （注意文件名<b>不带下划线</b>，这一点直接决定了 datagen 怎么写，见
     * {@link #heartLoseFrames()}）。</p>
     *
     * <p><b>必须按顺序播放</b>：datagen 侧用 {@code spriteSet(type, Iterable)}
     * 把五帧按 0→4 的顺序写成一张有序列表，客户端
     * {@code HeartLoseParticle.tick()} 再用 {@code setSpriteFromAge} 逐帧推进。
     * 描述文件本身不含动画信息，顺序完全由这里的数组决定。</p>
     *
     * <p>与 {@code ULCERATED_MEAT} 的区别：那个是随机取一帧的静态贴图
     * （{@code pickSprite}），这个是固定顺序的动画。用 {@code sprite(...)}
     * 会把 "heart_lose" 当成一张不存在的单图，图集里找不到就成了紫黑缺失贴图。</p>
     */
    public static final RegistryObject<SimpleParticleType> HEART_LOSE =
            PARTICLE_TYPES.register("heart_lose", () -> new SimpleParticleType(false));

    /** {@code heart_lose} 的序列帧数量（heart_lose0 .. heart_lose4）。 */
    public static final int HEART_LOSE_FRAMES = 5;

    /**
     * {@code heart_lose} 的有序帧列表 —— datagen 与客户端共用同一份顺序定义。
     *
     * <p>刻意<b>不用</b> Forge 那个 {@code spriteSet(type, baseName, count, reverse)}
     * 重载，原因有二（都是实测撞出来的）：</p>
     * <ol>
     *   <li>那个重载拼出来的名字是 {@code baseName + "_" + 序号}（即
     *       {@code heart_lose_0}），与美术实际给的 {@code heart_lose0} 不符，
     *       datagen 会直接抛 "Texture ... does not exist in any known resource pack"；</li>
     *   <li>它的第四个参数是 {@code reverse}（倒放），不是"开启动画"——
     *       序列播放由客户端 {@code setSpriteFromAge} 负责。</li>
     * </ol>
     * <p>所以这里自己给出帧列表：命名不受约束，顺序一眼可见。</p>
     */
    public static java.util.List<net.minecraft.resources.ResourceLocation> heartLoseFrames() {
        java.util.List<net.minecraft.resources.ResourceLocation> frames = new java.util.ArrayList<>(HEART_LOSE_FRAMES);
        for (int i = 0; i < HEART_LOSE_FRAMES; i++) {
            frames.add(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "heart_lose" + i));
        }
        return frames;
    }

    /**
     * 血滴 —— 命中 / 受伤时溅出来的血点：有方块碰撞、会弹跳，落地"滴答"后继续滚跳，
     * 存活约 6 秒后缩小淡出（手感见 {@code BloodParticle}）。
     *
     * <p>贴图：{@code assets/hall/textures/particle/blood.png}，<b>单张</b>精灵
     * （不是序列帧，所以 datagen 用 {@code sprite(...)} 而不是 {@code spriteSet(...)}）；
     * 粒子描述由 datagen（{@code ParticleData}）生成到
     * {@code assets/hall/particles/blood.json}；
     * 客户端提供器在 {@code ClientModEventHandler#onRegisterParticleProviders} 里注册。</p>
     *
     * <p>参数 {@code false} = 不绕过粒子的距离剔除。参考 mod（corruption）里 {@code blood}
     * 传的是 {@code true}（overrideLimiter）；hall 这三个新粒子统一按 {@code false} 处理 ——
     * 这个开关只影响"离玩家很远时还要不要发这个粒子"，对画面表现没有别的副作用，
     * 统一取值以后排查"粒子发不出来"时少一个变量。</p>
     */
    public static final RegistryObject<SimpleParticleType> BLOOD =
            PARTICLE_TYPES.register("blood", () -> new SimpleParticleType(false));

    /**
     * 肉块 —— 爆体 / 尸块碎裂时炸出来的肉屑。
     *
     * <p>贴图：{@code assets/hall/textures/particle/meat.png}（单张精灵）；
     * 描述文件由 datagen（{@code ParticleData.sprite}）生成到
     * {@code assets/hall/particles/meat.json}。
     * 它和 {@link #ULCERATED_MEAT} 的区别在<b>客户端行为</b>（大、会弹跳、随寿命缩小），
     * 不在注册层 —— 两者都是一张静态贴图的 {@code SimpleParticleType}。</p>
     *
     * <p>服务端"爆体"时由 {@code MeatBurstPacket} 触发，客户端用
     * {@code EntityParticleUtils.spawnParticles} 在这个粒子上撒 30 颗。</p>
     */
    public static final RegistryObject<SimpleParticleType> MEAT =
            PARTICLE_TYPES.register("meat", () -> new SimpleParticleType(false));

    /**
     * 血腥爆炸 —— 爆体瞬间在实体位置炸开的一团血雾，16 帧序列动画。
     *
     * <p>贴图是 16 张序列帧：
     * {@code assets/hall/textures/particle/explosion_bloody0.png} ~
     * {@code explosion_bloody15.png}（文件名<b>不带下划线</b>，和 {@code heart_lose0..4}
     * 是同一套命名习惯）。顺序由 {@link #explosionBloodyFrames()} 给出，datagen 写成
     * 有序数组，客户端 {@code ExplosionBloodyParticle} 再用 {@code setSpriteFromAge}
     * 按 age 逐帧推进。</p>
     *
     * <p>参数 {@code true} = overrideLimiter：爆体可能发生在离玩家稍远的位置，
     * 不该因为"粒子距离剔除"在视野边缘突然整团消失（参考 mod 同样传 {@code true}）。</p>
     */
    public static final RegistryObject<SimpleParticleType> EXPLOSION_BLOODY =
            PARTICLE_TYPES.register("explosion_bloody", () -> new SimpleParticleType(true));

    /** {@code explosion_bloody} 的序列帧数量（explosion_bloody0 .. explosion_bloody15）。 */
    public static final int EXPLOSION_BLOODY_FRAMES = 16;

    /**
     * {@code explosion_bloody} 的有序帧列表 —— datagen 与客户端共用同一份顺序定义。
     *
     * <p>和 {@link #heartLoseFrames()} 一样<b>逐帧枚举</b>，理由也完全相同：
     * Forge 的 {@code spriteSet(type, baseName, count, reverse)} 拼出来的名字是
     * {@code baseName + "_" + 序号}（即 {@code explosion_bloody_0}），
     * 而美术给的文件名是 {@code explosion_bloody0}（不带下划线），
     * 用它会在 datagen 期直接抛
     * "Texture 'hall:explosion_bloody_15' does not exist in any known resource pack"。</p>
     *
     * <p>帧数被客户端 {@code ExplosionBloodyParticle} 直接当作 {@code lifetime} 用，
     * 两者必须相等（都取 {@link #EXPLOSION_BLOODY_FRAMES}），否则 {@code SpriteSet}
     * 的整数除法取帧会跳帧或播不完 —— 推导见那个类的注释。</p>
     */
    public static java.util.List<net.minecraft.resources.ResourceLocation> explosionBloodyFrames() {
        java.util.List<net.minecraft.resources.ResourceLocation> frames =
                new java.util.ArrayList<>(EXPLOSION_BLOODY_FRAMES);
        for (int i = 0; i < EXPLOSION_BLOODY_FRAMES; i++) {
            frames.add(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    HallMod.MODID, "explosion_bloody" + i));
        }
        return frames;
    }
}
