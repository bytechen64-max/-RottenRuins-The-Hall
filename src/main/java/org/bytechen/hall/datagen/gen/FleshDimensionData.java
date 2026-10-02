package org.bytechen.hall.datagen.gen;

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
import org.bytechen.hall.overworld.registry.HallDimensions;
import org.bytechen.hall.overworld.registry.RegisterBlock;

import java.util.List;
import java.util.OptionalLong;

/**
 * 血肉庭园维度（{@code hall:heall}）数据生成。
 * <p>
 * 维度定义拆成三块，全部经由 datapack 注册表产出：
 * <ul>
 *   <li>{@link #FLESH_MARROW_NOISE} 噪声设置。开放天空的宏伟王庭地貌，
 *       地表铺王庭草方块 / 王庭泥土 / 王庭石块，不新增任何方块。</li>
 *   <li>{@link #HEALL_TYPE} 维度类型。开放天空、允许天光、恒为正午，
 *       与王庭群系的苍白天空配套。</li>
 *   <li>{@link #HEALL_STEM} 维度本体。噪声区块生成器 + 固定血肉群系。</li>
 * </ul>
 *
 * <h2>高度约定</h2>
 * 维度类型（min_y=0 / height=256）必须与噪声设置的
 * {@code min_y / height} 完全一致，否则区块生成会越界。
 *
 * <h2>天空为什么可见（踩过的坑）</h2>
 * 最初这个维度把 {@code hasCeiling} 设成 {@code true}、{@code hasSkylight}
 * 设成 {@code false}，并在客户端用 {@code RegisterDimensionSpecialEffectsEvent}
 * 注册了自定义维度特效，结果天空完全不渲染。三个条件必须同时满足天空才可见：
 * 维度类型不要盖顶、允许天光，并且不要注册自定义维度特效
 * （那会顶掉 Forge 的默认实现）。
 */
public final class FleshDimensionData {

    /** 维度唯一标识，同时作为维度类型与维度的注册名（{@code hall:heall}） */
    public static final ResourceLocation HEALL_ID = HallDimensions.HEALL_ID;

    /** 噪声设置 */
    public static final ResourceKey<NoiseGeneratorSettings> FLESH_MARROW_NOISE =
            ResourceKey.create(Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath("hall", "flesh_marrow"));

    /**
     * 自定义地形噪声（{@code hall:terrain}）。
     * <p>
     * 参数定义见 {@link HallNoiseData}：firstOctave = -5（主波长约 32 格）、
     * amplitudes = [1.0, 0.5, 0.25]。
     * <p>
     * 为什么必须自建噪声：原版内建噪声的输出幅度无法从「首八度」推断，
     * 本维度曾因此在「一望无际的平原」与「顶到世界顶被削平」之间反复横跳三次。
     * 自建噪声把八度与幅度显式写死，量级才是确定的。
     */
    public static final ResourceKey<NormalNoise.NoiseParameters> TERRAIN_NOISE = HallNoiseData.TERRAIN;

    /** 自定义细节噪声（{@code hall:detail}）：叠加在地形上的次级起伏 */
    public static final ResourceKey<NormalNoise.NoiseParameters> DETAIL_NOISE = HallNoiseData.DETAIL;

    /** 维度类型 */
    public static final ResourceKey<DimensionType> HEALL_TYPE = HallDimensions.HEALL_TYPE;

    /** 维度本体（LevelStem） */
    public static final ResourceKey<LevelStem> HEALL_STEM = HallDimensions.HEALL_STEM;

    /**
     * 运行时的维度键（{@link net.minecraft.world.level.Level}）。
     * <p>
     * 与 {@link #HEALL_STEM} 是同一个 ResourceLocation：
     * 存档里的 LevelStem 载入后会以此键注册成实际的 Level。
     */
    public static final ResourceKey<Level> HEALL_LEVEL = HallDimensions.HEALL_LEVEL;

    /**
     * 维度可用的高度范围。
     * <p>
     * 必须给山留足空间。最初用的是 min_y=0 / height=128，结果地表峰顶
     * 越过 y=128 后被建筑高度上限削掉，最上面一层变成一望无际的平顶
     * （方块上限之外是 out of this world，无法生成方块）。
     * 现在抬到 256 格，并把地表基准放在 y=128，上下各留约 128 格给起伏。
     * <p>
     * 此值必须与 {@code NoiseSettings.create(...)} 的参数以及
     * {@code DimensionType} 的 min_y / height / logical_height 三者保持一致。
     */
    private static final int MIN_Y = 0;
    private static final int HEIGHT = 256;

    /** 地形噪声增益。改大 = 起伏更猛，改小 = 更平缓。详见下方标定说明。 */
    private static final double TERRAIN_GAIN = 4.0D;

    /** 细节噪声增益。只做表面质感，不宜过大。 */
    private static final double DETAIL_GAIN = 0.8D;

    private FleshDimensionData() {}

    // ==================== 噪声设置 ====================

    /**
     * 血肉庭园的地形设置，开放天空、宏伟起伏的王庭地貌。
     * <p>
     * 不引用「键控密度函数」（如 {@code minecraft:nether/base_3d_noise}），
     * 因为那些键在 datagen 的空白 RegistrySetBuilder 里并未绑定；
     * 而是用 {@link DensityFunctions} 的原语现场拼出地形骨架。
     * <p>
     * 噪声必须带键且幅度可控：{@code RandomState} 的噪声装配器会对每个
     * {@code DensityFunctions.Noise} 节点执行
     * {@code lookup.get(key).orElseThrow()}，所以不能用无键的
     * {@code Holder.direct(...)}（那会在开服时抛 {@code NoSuchElementException}）。
     * 同时噪声的幅度也必须可控，原版内建噪声做不到这一点，
     * 故改用自建的 {@link #TERRAIN_NOISE} / {@link #DETAIL_NOISE}。
     */
    public static void bootstrapNoiseSettings(BootstapContext<NoiseGeneratorSettings> context) {
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);

        // ============== 地形幅度标定（改动前请务必读完） ==============
        //
        // 【地表高度与密度的关系】
        // 地表出现在 density = 0 处，而 yClampedGradient 在区间内是斜率 k
        // 的直线，因此：      起伏格数 约等于 (增益 乘 噪声幅度) / k
        //
        // 【踩过的坑（本维度为此返工多次，请勿重复）】
        // 1. 绝不要用 DensityFunctions.mappedNoise(...)。
        //    它会在序列化时插入归一化项，参数凑巧时把噪声整体乘成 0.0
        //    （生成物里会出现 "argument1": 0.0），噪声彻底失效，得到纯平原。
        //    正确做法是用 DensityFunctions.noise(holder)。
        // 2. 原版内建噪声的幅度无法从首八度推断（shift 得到真平原、
        //    pillar 又顶到世界顶）。故用 HallNoiseData 里自建的
        //    hall:terrain / hall:detail，八度与幅度显式写死。
        // 3. 渐变斜率太小会把噪声稀释掉，用陡斜率（见下）。
        // 4. 维度太矮会让峰顶撞上限被削平，故 height=256。
        //
        // 【本版参数】
        //   hall:terrain   amplitudes [1.0, 0.5, 0.25]
        //   渐变           y=-64 处 +20.0 到 y=320 处 -20.0
        //                  零点正好在 y=128，斜率 k = 40/384 约等于 0.104
        //   增益 A = 4.0（见 TERRAIN_GAIN）
        //   基准地表 y=128，地表大致落在 y = 60 到 195，上下都留有富余
        //
        // 【调参指南（改完跑 runData，并删掉该维度的 region 目录）】
        //   还是太平  调大 TERRAIN_GAIN（4.0 到 8.0）
        //   撞到 255 或 0   调小 TERRAIN_GAIN
        //   地表整体偏高偏低   调整下方 yClampedGradient 的 v0（每 1.0 约 9.6 格）
        //   山体太碎太宽   改 HallNoiseData 里 TERRAIN 的 firstOctave
        // ==============================================================
        DensityFunction terrainNoise = DensityFunctions.noise(noises.getOrThrow(TERRAIN_NOISE));
        DensityFunction detailNoise = DensityFunctions.noise(noises.getOrThrow(DETAIL_NOISE));

        // 基准渐变跨越整个可用高度：y=-64 到 +20.0，y=320 到 -20.0
        // 零点是 y=128，正好是这个维度的中段，上下各留 128 格给起伏
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
                // 深层默认方块：王庭石块
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

    /**
     * 地表规则，完全复用现有王庭方块族，不引入任何新方块。
     * <p>
     * 规则按顺序匹配，第一条命中即生效：
     * <ol>
     *   <li>最表层 1 格：王庭草方块（带草色，构成王庭草原）</li>
     *   <li>表层以下 3 格：王庭泥土（过渡层）</li>
     *   <li>其余深层：王庭石块</li>
     * </ol>
     * stoneDepthCheck 只命中真正的地表（含洞窟内壁），
     * 因此山体内部与沟壑侧壁也会自然铺上草与泥土，不会有裸露的怪面。
     */
    private static SurfaceRules.RuleSource createHallSurfaceRule() {
        BlockState grass = RegisterBlock.HALL_GRASS_BLOCK.get().defaultBlockState();
        BlockState dirt = RegisterBlock.HALL_DIRT.get().defaultBlockState();
        BlockState stone = RegisterBlock.HALL_STONE.get().defaultBlockState();

        return SurfaceRules.sequence(
                // 1. 最表层，王庭草方块
                SurfaceRules.ifTrue(
                        SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                        SurfaceRules.state(grass)),
                // 2. 表层以下 3 格，王庭泥土
                SurfaceRules.ifTrue(
                        SurfaceRules.stoneDepthCheck(0, true, 0, CaveSurface.FLOOR),
                        SurfaceRules.state(dirt)),
                // 3. 其余，王庭石块
                SurfaceRules.state(stone)
        );
    }

    // ==================== 维度类型 ====================

    /**
     * 血肉庭园维度类型，开放天空。
     * <p>
     * 与最初版本的关键区别（最初看不到天空的原因）：
     * <ul>
     *   <li>hasCeiling 由 true 改为 false，不再盖顶</li>
     *   <li>hasSkyLight 由 false 改为 true，允许天光</li>
     *   <li>fixedTime 改为 6000（正午），与主世界一致，保证天空可见</li>
     *   <li>ambientLight 归零，不做恒定自发光，让天光正常参与</li>
     * </ul>
     * 另需注意：客户端不能用 RegisterDimensionSpecialEffectsEvent 注册
     * 自定义特效，否则会顶掉 Forge 的默认实现，天空直接不渲染。
     * 这里不注册任何特效，天空颜色由群系自身的 skyColor 决定
     * （血肉群系已设为与王庭一致的苍白色）。
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
                ResourceLocation.fromNamespaceAndPath("hall", "heall"), // effects
                0.0F,                         // ambientLight，交给天光
                monsterSettings
        ));
    }

    // ==================== 维度本体 ====================

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
