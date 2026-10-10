package org.bytechen.hall.datagen.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.bytechen.hall.HallMod;

/**
 * Hall 自定义噪声参数。
 * <p>
 * <b>为什么必须自建噪声，而不是用原版内建的：</b>
 * 原版噪声（{@code minecraft:shift} / {@code cave_cheese} / {@code pillar} …）
 * 的输出幅度是为各自用途调过的，且<b>无法从「首八度」推断</b>。
 * 血肉庭园维度最初直接借原版噪声驱动地形高度，结果在
 * 「一望无际的平原」与「整张图顶到世界顶被削平」之间反复横跳三次。
 * 自建噪声把八度与幅度显式写死，量级完全可控。
 *
 * <h2>为什么定义在 Java 里而不是 JSON 里</h2>
 * datagen 的 {@link net.minecraft.core.RegistrySetBuilder} 只接受 bootstrap
 * 函数，无法从数据包文件载入注册表内容（也读不到自身尚未产出的文件）。
 * 而运行时那条路（手写 JSON 放进 {@code src/main/resources}）会让
 * datagen 阶段的引用解析不到这些键而直接失败。
 * 因此这里用 bootstrap 直接注册 —— 两种场景都能工作。
 *
 * <h2>幅度约定</h2>
 * {@code NormalNoise} 各八度叠加后，实际输出范围通常显著小于
 * 「幅度总和」（各八度会彼此抵消）。经验上有效幅度约为幅度和的 1/4 ~ 1/2。
 * 调参时以实际观感为准，见 {@code FleshDimensionData.bootstrapNoiseSettings}
 * 里的标定说明。
 */
public final class HallNoiseData {

    /** 地形噪声：主波长约 32 格，用于造就山脊与沟壑 */
    public static final ResourceKey<NormalNoise.NoiseParameters> TERRAIN =
            ResourceKey.create(Registries.NOISE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "terrain"));

    /** 细节噪声：主波长约 4 格，用于表面破碎感 */
    public static final ResourceKey<NormalNoise.NoiseParameters> DETAIL =
            ResourceKey.create(Registries.NOISE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "detail"));

    private HallNoiseData() {}

    /**
     * 注册原版噪声 + 本模组噪声。
     * <p>
     * 原版的 {@link NoiseData#bootstrap} 也必须调用 —— 地表规则里用到的
     * {@code minecraft:patch} 等键需要被绑定，否则解析时会报
     * {@code Trying to access unbound value}。
     */
    public static void bootstrap(BootstapContext<NormalNoise.NoiseParameters> context) {
        // 1. 原版噪声
        NoiseData.bootstrap(context);

        // 2. 本模组噪声（八度与幅度显式写死）
        context.register(TERRAIN, new NormalNoise.NoiseParameters(-5, 1.0D, 0.5D, 0.25D));
        context.register(DETAIL, new NormalNoise.NoiseParameters(-3, 1.0D, 0.5D));
    }
}
