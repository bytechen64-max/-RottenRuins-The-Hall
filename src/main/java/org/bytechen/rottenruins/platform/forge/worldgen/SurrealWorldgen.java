package org.bytechen.rottenruins.platform.forge.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.worldgen.IslandVegetationFeature;
import org.bytechen.rottenruins.platform.minecraft.worldgen.SurrealTerrainDensity;

public final class SurrealWorldgen {
    private static final DeferredRegister<Codec<? extends DensityFunction>> DENSITIES=
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE,HallMod.MODID);
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,HallMod.MODID);
    public static final RegistryObject<IslandVegetationFeature> ISLAND_VEGETATION=
            FEATURES.register("island_vegetation",IslandVegetationFeature::new);
    static {DENSITIES.register("surreal_terrain",()->SurrealTerrainDensity.CODEC.codec());}
    private SurrealWorldgen() {}
    public static void register(IEventBus bus) {DENSITIES.register(bus);FEATURES.register(bus);}
}
