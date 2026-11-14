package org.bytechen.rottenruins.platform.forge.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.content.worldgen.GardenMonumentPiece;
import org.bytechen.rottenruins.content.worldgen.GardenMonumentStructure;

/** Forge registration only. Geometry, placement and block generation live outside this adapter. */
public final class GardenWorldgen {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, HallMod.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, HallMod.MODID);

    static {
        TYPES.register("garden_monument", () -> GardenMonumentStructure.TYPE);
        PIECES.register("garden_monument_slice", () -> GardenMonumentPiece.TYPE);
    }

    private GardenWorldgen() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }
}
