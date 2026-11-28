package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;

import java.util.List;

/** 血肉庭园矿脉：只替换王庭石块，由庭园陆地与海洋群系共同引用。 */
public final class HallOreData {
    public static final ResourceKey<ConfiguredFeature<?, ?>> DOMITE_ORE = configuredKey("ore_domite");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DOMERITE_ORE = configuredKey("ore_domerite");
    public static final ResourceKey<PlacedFeature> DOMITE_ORE_PLACED = placedKey("ore_domite");
    public static final ResourceKey<PlacedFeature> DOMERITE_ORE_PLACED = placedKey("ore_domerite");

    public static final ResourceKey<PlacedFeature> DOMITE_SKY = placedKey("ore_domite_sky");
    public static final ResourceKey<PlacedFeature> DOMERITE_SKY = placedKey("ore_domerite_sky");

    private HallOreData() {}

    public static void bootstrapConfiguredFeatures(BootstapContext<ConfiguredFeature<?, ?>> context) {
        BlockMatchTest stone = new BlockMatchTest(RegisterBlock.HALL_STONE.get());
        context.register(DOMITE_ORE, new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(stone, RegisterBlock.DOMITE_MINERAL.get().defaultBlockState(), 5)));
        context.register(DOMERITE_ORE, new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(stone, RegisterBlock.DOMERITE_MINERAL.get().defaultBlockState(), 8)));
    }

    public static void bootstrapPlacedFeatures(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);
        // 三角分布：胚晶集中在 Y=36 附近，云铁集中在 Y=68 附近。
        context.register(DOMITE_SKY, placed(features, DOMITE_ORE, 4, 128, 672));
        context.register(DOMERITE_SKY, placed(features, DOMERITE_ORE, 8, 128, 672));
        context.register(DOMITE_ORE_PLACED, placed(features, DOMITE_ORE, 4, 8, 64));
        context.register(DOMERITE_ORE_PLACED, placed(features, DOMERITE_ORE, 8, 8, 128));
    }

    private static PlacedFeature placed(HolderGetter<ConfiguredFeature<?, ?>> features,
                                        ResourceKey<ConfiguredFeature<?, ?>> key,
                                        int count, int minY, int maxY) {
        return new PlacedFeature(features.getOrThrow(key), List.of(
                CountPlacement.of(count),
                InSquarePlacement.spread(),
                HeightRangePlacement.triangle(VerticalAnchor.absolute(minY), VerticalAnchor.absolute(maxY)),
                BiomeFilter.biome()));
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> configuredKey(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path));
    }

    private static ResourceKey<PlacedFeature> placedKey(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path));
    }
}
