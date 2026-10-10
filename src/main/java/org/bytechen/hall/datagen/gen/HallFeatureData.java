package org.bytechen.hall.datagen.gen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.RegisterFeature;
import org.bytechen.hall.overworld.registry.feature.HallVineFeature;

import java.util.List;

/**
 * Hall 世界生成中的地物（树木、植被）数据生成。
 * <p>
 * 分三层，与游戏内的注册层次一一对应：
 * <ol>
 *   <li><b>Feature</b> —— Java 逻辑，见 {@link RegisterFeature}；
 *       已由 {@code minecraft:feature} 注册表在 mod 构造期注册。</li>
 *   <li><b>ConfiguredFeature</b> —— 「用什么配置生成什么」。</li>
 *   <li><b>PlacedFeature</b> —— 「在哪、多密、什么高度」。</li>
 * </ol>
 * 群系（{@code HallBiomeData}）最终引用的是 PlacedFeature。
 *
 * <h2>数量约定</h2>
 * 树木用「每区块 N 次尝试」；草丛与花丛用较高的次数以获得成片的密度，
 * 单次尝试失败（下方无支撑、上方被挡）不会消耗额外开销。
 */
public final class HallFeatureData {

    // ==================== 树木 ====================

    /** 单棵王庭树（配置） */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_tree"));

    /** 王庭树林（每区块尝试 12 次，贴高度图放置） */
    public static final ResourceKey<PlacedFeature> HALL_TREES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_trees"));

    // ==================== 草丛 ====================

    /** 单丛王庭草（配置） */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_GRASS_PATCH =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_grass_patch"));

    /** 王庭草丛（每区块尝试 10 次，每次撒 48 格） */
    public static final ResourceKey<PlacedFeature> HALL_GRASS_PATCHES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_grass_patches"));

    // ==================== 花丛 ====================

    /** 单丛王庭花（配置） */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_FLOWER_PATCH =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_flower_patch"));

    /** 王庭花丛（每区块尝试 3 次，比草稀疏） */
    public static final ResourceKey<PlacedFeature> HALL_FLOWER_PATCHES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_flower_patches"));

    // ==================== 藤蔓 ====================

    /** 王庭藤蔓（向下，从崖壁与洞顶垂挂） */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_VINE_DOWN =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vine_down"));

    /** 王庭藤蔓（向上，从地面竖着长起来） */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_VINE_UP =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vine_up"));

    /** 垂挂藤蔓丛（每区块 2 次） */
    public static final ResourceKey<PlacedFeature> HALL_VINES_DOWN =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vines_down"));

    /** 直立藤蔓丛（每区块 12 次，密一些才显眼） */
    public static final ResourceKey<PlacedFeature> HALL_VINES_UP =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vines_up"));

    private HallFeatureData() {}

    // ==================== ConfiguredFeature ====================

    public static void bootstrapConfiguredFeatures(BootstapContext<ConfiguredFeature<?, ?>> context) {
        HolderGetter<Feature<?>> features = context.lookup(Registries.FEATURE);

        // 无配置地物
        context.register(HALL_TREE, configured(context, features,
                RegisterFeature.HALL_TREE.get(), NoneFeatureConfiguration.INSTANCE));
        context.register(HALL_GRASS_PATCH, configured(context, features,
                RegisterFeature.HALL_GRASS_PATCH.get(), NoneFeatureConfiguration.INSTANCE));
        context.register(HALL_FLOWER_PATCH, configured(context, features,
                RegisterFeature.HALL_FLOWER_PATCH.get(), NoneFeatureConfiguration.INSTANCE));

        // 藤蔓需要 Config 区分生长方向
        context.register(HALL_VINE_DOWN, configured(context, features,
                RegisterFeature.HALL_VINE_DOWN.get(), HallVineFeature.Config.down()));
        context.register(HALL_VINE_UP, configured(context, features,
                RegisterFeature.HALL_VINE_UP.get(), HallVineFeature.Config.up()));
    }

    /**
     * 把「已注册的 Feature 实例」包装成 ConfiguredFeature。
     * <p>
     * 需要一个带 ResourceKey 的 Holder 才能放进 datapack 注册表，
     * 而 {@code DeferredRegister} 的 {@code getKey()} 泛型不易推导，
     * 因此这里按注册名反查，顺带在未注册时立刻抛出可读的错误。
     */
    @SuppressWarnings("unchecked")
    private static <C extends FeatureConfiguration> ConfiguredFeature<?, ?> configured(
            BootstapContext<ConfiguredFeature<?, ?>> context,
            HolderGetter<Feature<?>> features,
            Feature<C> feature,
            C config) {

        ResourceKey<Feature<?>> key = BuiltInRegistries.FEATURE
                .getResourceKey(feature)
                .orElseThrow(() -> new IllegalStateException(
                        "地物尚未注册，检查 RegisterFeature 是否挂上了事件总线"));

        Holder<Feature<C>> holder =
                (Holder<Feature<C>>) (Holder<?>) features.getOrThrow(key);

        return new ConfiguredFeature<>(holder.value(), config);
    }

    // ==================== PlacedFeature ====================

    public static void bootstrapPlacedFeatures(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configured = context.lookup(Registries.CONFIGURED_FEATURE);

        // 王庭树林：尝试次数给到 5，因为间距校验会拒掉相当一部分尝试
        // （成功率约一半），实际成树约 2~3 棵/区块。
        //
        // 关于密度与间距（踩过的坑）：树冠半径远大于相邻基点的间距，
        // 如果只写 CountPlacement + HeightmapPlacement，多次尝试会集中在
        // 区块中心的极小范围内，树冠必然互相穿插、糊成一整片。
        // 因此必须插入 InSquarePlacement 把尝试点铺满整个区块；
        // 真正的间距约束由 HallTreeFeature.hasNearbyTree 负责。
        context.register(HALL_TREES, placed(configured, HALL_TREE, List.of(
                CountPlacement.of(5),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 王庭草丛：8 次 × 48 格，铺满区块
        context.register(HALL_GRASS_PATCHES, placed(configured, HALL_GRASS_PATCH, List.of(
                CountPlacement.of(8),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 王庭花丛：数量刻意压低 —— 花是点缀，成片反而破坏氛围。
        // （曾用 4 → 3，现降到 1；每次仍会撒 48 格，所以一朵花丛依然是成簇的）
        context.register(HALL_FLOWER_PATCHES, placed(configured, HALL_FLOWER_PATCH, List.of(
                CountPlacement.of(1),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 王庭藤蔓（垂挂）：地物只放置「一格」作为种子，之后由方块自身的
        // randomTick 逐节往下长（上限见 HallVineBlock.MAX_DOWN_HEIGHT = 10）。
        // 因此这里数量压到最低 —— 播一个点，剩下的交给生长。
        context.register(HALL_VINES_DOWN, placed(configured, HALL_VINE_DOWN, List.of(
                CountPlacement.of(1),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 王庭藤蔓（直立）：同样只播一格，之后向上长
        // （上限见 HallVineBlock.MAX_UP_HEIGHT = 4）。
        // 数量由 12 → 4 → 2 逐步下调；因为会自行生长，播太多会满地都是。
        // 注意 InSquarePlacement 与 HeightmapPlacement 都必须保留，
        // 否则尝试点会挤在区块中心（与树木早年踩过的坑完全一样）。
        context.register(HALL_VINES_UP, placed(configured, HALL_VINE_UP, List.of(
                CountPlacement.of(2),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));
    }

    private static PlacedFeature placed(
            HolderGetter<ConfiguredFeature<?, ?>> configured,
            ResourceKey<ConfiguredFeature<?, ?>> feature,
            List<net.minecraft.world.level.levelgen.placement.PlacementModifier> modifiers) {
        return new PlacedFeature(configured.getOrThrow(feature), modifiers);
    }
}
