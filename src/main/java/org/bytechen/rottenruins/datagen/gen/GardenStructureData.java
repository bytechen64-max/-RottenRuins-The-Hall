package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.worldgen.GardenMonumentStructure;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Kind;

import java.util.Map;

public final class GardenStructureData {
    public static final ResourceKey<Structure> EMPTY_THRONE = key("empty_throne");
    public static final ResourceKey<Structure> SUNKEN_CROWN = key("sunken_crown");

    private GardenStructureData() {}

    public static void bootstrapStructures(BootstapContext<Structure> context) {
        var biomes = context.lookup(Registries.BIOME);
        context.register(EMPTY_THRONE, new GardenMonumentStructure(new Structure.StructureSettings(
                HolderSet.direct(biomes.getOrThrow(HallBiomeData.FLESH_MARROW)), Map.of(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, TerrainAdjustment.NONE), Kind.EMPTY_THRONE));
        context.register(SUNKEN_CROWN, new GardenMonumentStructure(new Structure.StructureSettings(
                HolderSet.direct(biomes.getOrThrow(HallBiomeData.HALL_OCEAN)), Map.of(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, TerrainAdjustment.NONE), Kind.SUNKEN_CROWN));
    }

    public static void bootstrapStructureSets(BootstapContext<StructureSet> context) {
        var structures = context.lookup(Registries.STRUCTURE);
        context.register(setKey("empty_thrones"), new StructureSet(structures.getOrThrow(EMPTY_THRONE),
                new RandomSpreadStructurePlacement(64, 24, RandomSpreadType.LINEAR, 193847561)));
        context.register(setKey("sunken_crowns"), new StructureSet(structures.getOrThrow(SUNKEN_CROWN),
                new RandomSpreadStructurePlacement(72, 28, RandomSpreadType.LINEAR, 817263549)));
    }

    private static ResourceKey<Structure> key(String path) {
        return ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path));
    }

    private static ResourceKey<StructureSet> setKey(String path) {
        return ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath(HallMod.MODID, path));
    }
}
