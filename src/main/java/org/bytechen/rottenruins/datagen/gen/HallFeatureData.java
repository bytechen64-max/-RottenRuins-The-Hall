package org.bytechen.rottenruins.datagen.gen;

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
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterFeature;
import org.bytechen.rottenruins.overworld.registry.feature.HallVineFeature;

import java.util.List;

/**
 * Hall 世界生成的植被地物数据：Feature（Java 逻辑，见 {@link RegisterFeature}）
 * → ConfiguredFeature（用什么配置）→ PlacedFeature（在哪、多密、什么高度），
 * 群系（{@code HallBiomeData}）引用的是最后一层。
 */
public final class HallFeatureData {

    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_tree"));

    public static final ResourceKey<PlacedFeature> HALL_TREES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_trees"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_GRASS_PATCH =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_grass_patch"));

    public static final ResourceKey<PlacedFeature> HALL_GRASS_PATCHES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_grass_patches"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_FLOWER_PATCH =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_flower_patch"));

    public static final ResourceKey<PlacedFeature> HALL_FLOWER_PATCHES =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_flower_patches"));

    /** 藤蔓（向下从崖壁与洞顶垂挂 / 向上从地面长起）：方向由 Config 区分，放置规则相同。 */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_VINE_DOWN =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vine_down"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> HALL_VINE_UP =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vine_up"));

    public static final ResourceKey<PlacedFeature> HALL_VINES_DOWN =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vines_down"));

    public static final ResourceKey<PlacedFeature> HALL_VINES_UP =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "hall_vines_up"));

    private HallFeatureData() {}

    public static void bootstrapConfiguredFeatures(BootstapContext<ConfiguredFeature<?, ?>> context) {
        HolderGetter<Feature<?>> features = context.lookup(Registries.FEATURE);

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
     * 把已注册的 Feature 实例包装成 ConfiguredFeature：datapack 注册表要求带 ResourceKey 的
     * Holder，而 {@code DeferredRegister.getKey()} 的泛型不好推导，故按注册名反查并强转。
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

    public static void bootstrapPlacedFeatures(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configured = context.lookup(Registries.CONFIGURED_FEATURE);

        // 必须保留 InSquarePlacement：否则多次尝试会挤在区块中心，树冠互相穿插糊成一片；
        // 真正的间距约束由 HallTreeFeature.hasNearbyTree 负责。
        context.register(HALL_TREES, placed(configured, HALL_TREE, List.of(
                CountPlacement.of(5),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        context.register(HALL_GRASS_PATCHES, placed(configured, HALL_GRASS_PATCH, List.of(
                CountPlacement.of(8),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 花是点缀，数量刻意压低；每次仍撒 48 格，所以单丛依然是成簇的
        context.register(HALL_FLOWER_PATCHES, placed(configured, HALL_FLOWER_PATCH, List.of(
                CountPlacement.of(1),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

        // 藤蔓地物只播一格作为种子，之后由 HallVineBlock 的 randomTick 逐节生长
        // （上限 MAX_DOWN_HEIGHT = 10 / MAX_UP_HEIGHT = 4），所以数量压到最低。
        context.register(HALL_VINES_DOWN, placed(configured, HALL_VINE_DOWN, List.of(
                CountPlacement.of(1),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES))));

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
