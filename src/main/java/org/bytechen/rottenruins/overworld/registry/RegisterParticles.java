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

    /** 溃烂肉屑：溃烂系生物（斥候 / 巨碑）身上周期性掉落的血肉粒子（单张精灵）。 */
    public static final RegistryObject<SimpleParticleType> ULCERATED_MEAT =
            PARTICLE_TYPES.register("ulcerated_meat", () -> new SimpleParticleType(false));

    /** 失心粒子：绯红誓约命中时打出的心形碎片，5 张序列帧按 0→4 顺序播放。 */
    public static final RegistryObject<SimpleParticleType> HEART_LOSE =
            PARTICLE_TYPES.register("heart_lose", () -> new SimpleParticleType(false));

    public static final int HEART_LOSE_FRAMES = 5;

    /** 逐帧枚举而非 Forge 的 {@code spriteSet} 重载：后者会拼成 {@code heart_lose_0}，与美术给的 {@code heart_lose0} 不符。 */
    public static java.util.List<net.minecraft.resources.ResourceLocation> heartLoseFrames() {
        java.util.List<net.minecraft.resources.ResourceLocation> frames = new java.util.ArrayList<>(HEART_LOSE_FRAMES);
        for (int i = 0; i < HEART_LOSE_FRAMES; i++) {
            frames.add(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "heart_lose" + i));
        }
        return frames;
    }

    /** 血滴：命中 / 受伤时溅出的血点（单张精灵；{@code false} = 不绕过粒子的距离剔除）。 */
    public static final RegistryObject<SimpleParticleType> BLOOD =
            PARTICLE_TYPES.register("blood", () -> new SimpleParticleType(false));

    /** 肉块：爆体 / 尸块碎裂时炸出来的肉屑（单张精灵）。 */
    public static final RegistryObject<SimpleParticleType> MEAT =
            PARTICLE_TYPES.register("meat", () -> new SimpleParticleType(false));

    /** 血腥爆炸：爆体瞬间炸开的血雾，16 帧序列动画；{@code true} = overrideLimiter，远处也不整团消失。 */
    public static final RegistryObject<SimpleParticleType> EXPLOSION_BLOODY =
            PARTICLE_TYPES.register("explosion_bloody", () -> new SimpleParticleType(true));

    public static final int EXPLOSION_BLOODY_FRAMES = 16;

    /** 与 {@link #heartLoseFrames()} 同理逐帧枚举（文件名不带下划线）；帧数须与客户端 {@code lifetime} 相等，否则取帧会跳帧。 */
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
