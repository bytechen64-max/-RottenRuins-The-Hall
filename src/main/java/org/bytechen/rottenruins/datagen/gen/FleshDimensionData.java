package org.bytechen.rottenruins.datagen.gen;
import org.bytechen.rottenruins.HallMod;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.bytechen.rottenruins.overworld.registry.HallDimensions;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;

import java.util.List;
import java.util.OptionalLong;

/**
 * 血肉庭园维度（{@code hall:heall}）数据生成：噪声设置 / 维度类型 / LevelStem 三件套。
 * <p>维度类型与噪声设置的 {@code min_y / height} 必须完全一致，否则区块生成越界。
 * 天空可见的三个硬条件：不盖顶（hasCeiling=false）、允许天光、<b>不</b>注册自定义维度特效（会顶掉 Forge 默认实现）。
 */
public final class FleshDimensionData {

    /** 维度唯一标识，同时作为维度类型与维度的注册名（{@code hall:heall}） */
    public static final ResourceLocation HEALL_ID = HallDimensions.HEALL_ID;

    public static final ResourceKey<NoiseGeneratorSettings> FLESH_MARROW_NOISE =
            ResourceKey.create(Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "flesh_marrow"));

    /**
     * 自定义地形噪声（{@code hall:terrain}）：firstOctave = -5、amplitudes = [1.0, 0.5, 0.25]（见 {@link HallNoiseData}）。
     * <p>必须自建：原版内建噪声的输出幅度无法从"首八度"推断，本维度曾因此在"一望无际的平原"与"顶到世界顶"之间反复横跳。
     */
    public static final ResourceKey<NormalNoise.NoiseParameters> TERRAIN_NOISE = HallNoiseData.TERRAIN;

    public static final ResourceKey<NormalNoise.NoiseParameters> DETAIL_NOISE = HallNoiseData.DETAIL;

    public static final ResourceKey<DimensionType> HEALL_TYPE = HallDimensions.HEALL_TYPE;

    public static final ResourceKey<LevelStem> HEALL_STEM = HallDimensions.HEALL_STEM;

    /** 运行时的维度键：与 {@link #HEALL_STEM} 同一个 ResourceLocation，存档里的 LevelStem 载入后以此键注册成 Level。 */
    public static final ResourceKey<Level> HEALL_LEVEL = HallDimensions.HEALL_LEVEL;

    /**
     * 维度可用高度范围，必须与 {@code NoiseSettings.create(...)} 参数以及 DimensionType 的 min_y / height / logical_height 一致。
     * <p>height=128 时峰顶会被建筑高度上限削成平顶，故抬到 256，地表基准 y=128，上下各留约 128 格起伏。
     */
    private static final int MIN_Y = 0;
    private static final int HEIGHT = 256;

    /** 地形噪声增益。改大 = 起伏更猛，改小 = 更平缓。 */
    private static final double TERRAIN_GAIN = 4.0D;

    private static final double DETAIL_GAIN = 0.8D;

    private FleshDimensionData() {}

    /**
     * 血肉庭园的地形设置。<b>不要</b>引用键控密度函数（如 {@code minecraft:nether/base_3d_noise}）：那些键在 datagen
     * 的空白 RegistrySetBuilder 里未绑定。噪声节点必须带键（{@code Holder.direct} 会让 RandomState 装配时抛
     * {@code NoSuchElementException}），故用 {@link #TERRAIN_NOISE} / {@link #DETAIL_NOISE}。
     */
    public static void bootstrapNoiseSettings(BootstapContext<NoiseGeneratorSettings> context) {
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);

        // 标定要点：地表出现在 density = 0 处，起伏格数 ≈ (增益 × 噪声幅度) / 渐变斜率 k。
        // 绝不要用 DensityFunctions.mappedNoise(...)：序列化时会插入归一化项，参数凑巧时把噪声整体乘成 0.0（纯平原）。
        // 渐变 y=-64(+20.0) → y=320(-20.0) 零点正好在 y=128，k ≈ 0.104；增益 4.0 时地表大致落在 y=60..195。
        DensityFunction terrainNoise = DensityFunctions.noise(noises.getOrThrow(TERRAIN_NOISE));
        DensityFunction detailNoise = DensityFunctions.noise(noises.getOrThrow(DETAIL_NOISE));

        DensityFunction coast = DensityFunctions.yClampedGradient(-64, 320, 20.0D, -20.0D);

        DensityFunction shaped = DensityFunctions.add(
                DensityFunctions.add(
                        coast,
                        DensityFunctions.mul(DensityFunctions.constant(TERRAIN_GAIN), terrainNoise)),
                DensityFunctions.mul(DensityFunctions.constant(DETAIL_GAIN), detailNoise));

        DensityFunction finalDensity = DensityFunctions.interpolated(
                DensityFunctions.blendDensity(shaped));

        DensityFunction zero = DensityFunctions.zero();

        NoiseRouter router = new NoiseRouter(
                DensityFunctions.constant(0.0D),   // barrier
                zero,                              // fluidLevelFloodednessNoise
                zero,                              // fluidLevelSpreadNoise
                zero,                              // lavaNoise
                zero,                              // temperature
                zero,                              // vegetation
                zero,                              // continents
                zero,                              // erosion
                zero,                              // depth
                zero,                              // ridges
                zero,                              // initialDensityWithoutJaggedness
                finalDensity,                      // finalDensity
                zero,                              // veinToggle
                zero,                              // veinRidged
                zero                               // veinGap
        );

        context.register(FLESH_MARROW_NOISE, new NoiseGeneratorSettings(
                NoiseSettings.create(MIN_Y, HEIGHT, 1, 2),
                RegisterBlock.HALL_STONE.get().defaultBlockState(),
                Blocks.WATER.defaultBlockState(),
                router,
                createHallSurfaceRule(),
                List.of(),      // spawnTarget
                32,             // seaLevel
                false,          // disableMobGeneration
                true,           // aquifersEnabled，开放地形需要含水层
                false,          // oreVeinsEnabled
                true            // useLegacyRandomSource
        ));
    }

    /** 地表规则：按顺序匹配取第一条，surface 草方块 / 3 格泥土 / 其余石块。stoneDepthCheck 让洞窟内壁与沟壑侧壁同样铺上。 */
    private static SurfaceRules.RuleSource createHallSurfaceRule() {
        BlockState grass = RegisterBlock.HALL_GRASS_BLOCK.get().defaultBlockState();
        BlockState dirt = RegisterBlock.HALL_DIRT.get().defaultBlockState();
        BlockState stone = RegisterBlock.HALL_STONE.get().defaultBlockState();

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                        SurfaceRules.state(grass)),
                SurfaceRules.ifTrue(
                        SurfaceRules.stoneDepthCheck(0, true, 0, CaveSurface.FLOOR),
                        SurfaceRules.state(dirt)),
                SurfaceRules.state(stone)
        );
    }

    /**
     * 血肉庭园维度类型，开放天空：hasCeiling=false、hasSkyLight=true、fixedTime=6000、ambientLight=0。
     * <p>客户端<b>不能</b>用 RegisterDimensionSpecialEffectsEvent 注册自定义特效（会顶掉 Forge 默认实现，天空不渲染）。
     */
    public static void bootstrapDimensionType(BootstapContext<DimensionType> context) {
        DimensionType.MonsterSettings monsterSettings = new DimensionType.MonsterSettings(
                false,                        // piglinSafe
                false,                        // hasRaids
                UniformInt.of(0, 7),          // monsterSpawnLightTest
                0                             // monsterSpawnBlockLightLimit
        );

        context.register(HEALL_TYPE, new DimensionType(
                OptionalLong.of(6000L),       // fixedTime，恒为正午，天空常亮
                true,                         // hasSkyLight，允许天光
                false,                        // hasCeiling，开放天空
                false,                        // ultrawarm
                true,                         // natural，让床等表现正常
                1.0D,                         // coordinateScale
                true,                         // bedWorks，可以睡觉，可长期生存
                false,                        // respawnAnchorWorks
                MIN_Y,
                HEIGHT,
                HEIGHT,                       // logicalHeight
                BlockTags.INFINIBURN_NETHER,  // infiniburn
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "heall"), // effects
                0.0F,                         // ambientLight，交给天光
                monsterSettings
        ));
    }

    public static void bootstrapLevelStem(BootstapContext<LevelStem> context) {
        HolderGetter<DimensionType> dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettings = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                new FixedBiomeSource(biomes.getOrThrow(HallBiomeData.FLESH_MARROW)),
                noiseSettings.getOrThrow(FLESH_MARROW_NOISE)
        );

        context.register(HEALL_STEM, new LevelStem(
                dimensionTypes.getOrThrow(HEALL_TYPE),
                generator
        ));
    }
}
