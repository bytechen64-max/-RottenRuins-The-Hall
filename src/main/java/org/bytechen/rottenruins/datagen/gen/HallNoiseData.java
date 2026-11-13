package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.bytechen.rottenruins.HallMod;

/** Hall 自定义噪声参数；八度与幅度显式写死，量级可控；必须走 bootstrap 注册，datagen 读不到自身未产出的 JSON。 */
public final class HallNoiseData {

    /* 主波长：terrain 约 32 格（山脊、沟壑），detail 约 4 格（表面破碎感） */
    public static final ResourceKey<NormalNoise.NoiseParameters> TERRAIN =
            ResourceKey.create(Registries.NOISE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "terrain"));

    public static final ResourceKey<NormalNoise.NoiseParameters> DETAIL =
            ResourceKey.create(Registries.NOISE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "detail"));

    private HallNoiseData() {}

    /** 原版 NoiseData.bootstrap 也必须调用，否则地表规则引用的键 unbound 会报 Trying to access unbound value。 */
    public static void bootstrap(BootstapContext<NormalNoise.NoiseParameters> context) {
        NoiseData.bootstrap(context);

        context.register(TERRAIN, new NormalNoise.NoiseParameters(-5, 1.0D, 0.5D, 0.25D));
        context.register(DETAIL, new NormalNoise.NoiseParameters(-3, 1.0D, 0.5D));
    }
}
