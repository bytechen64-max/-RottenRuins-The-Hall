package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import org.bytechen.rottenruins.platform.forge.worldgen.SurrealWorldgen;
import java.util.List;

public final class SurrealFeatureData {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("rotten_ruins","island_vegetation");
    private static final ResourceKey<ConfiguredFeature<?,?>> CONFIGURED=ResourceKey.create(Registries.CONFIGURED_FEATURE,ID);
    public static final ResourceKey<PlacedFeature> ISLAND_VEGETATION=ResourceKey.create(Registries.PLACED_FEATURE,ID);
    private SurrealFeatureData() {}
    public static void configured(BootstapContext<ConfiguredFeature<?,?>> context) {
        context.register(CONFIGURED,new ConfiguredFeature<>(SurrealWorldgen.ISLAND_VEGETATION.get(),NoneFeatureConfiguration.INSTANCE));
    }
    public static void placed(BootstapContext<PlacedFeature> context) {
        context.register(ISLAND_VEGETATION,new PlacedFeature(context.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(CONFIGURED),List.of(BiomeFilter.biome())));
    }
}
