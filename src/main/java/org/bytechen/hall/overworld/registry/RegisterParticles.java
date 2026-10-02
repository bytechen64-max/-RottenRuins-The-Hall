package org.bytechen.hall.overworld.registry;

import org.bytechen.hall.HallMod;
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
}
