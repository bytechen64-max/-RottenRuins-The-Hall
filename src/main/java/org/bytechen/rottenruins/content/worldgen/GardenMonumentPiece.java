package org.bytechen.rottenruins.content.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Material;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Placement;
import org.bytechen.rottenruins.overworld.registry.RegisterBlock;
import org.bytechen.rottenruins.platform.minecraft.worldgen.GardenPieceCodec;

/** A single chunk slice. Never reads or writes blocks in neighboring chunks. */
public final class GardenMonumentPiece extends StructurePiece {
    public static final StructurePieceType TYPE = (context, tag) -> new GardenMonumentPiece(tag);
    private final Placement placement;

    public GardenMonumentPiece(Placement placement, BoundingBox box) {
        super(TYPE, 0, box);
        this.placement = placement;
    }

    private GardenMonumentPiece(CompoundTag tag) {
        super(TYPE, tag);
        this.placement = GardenPieceCodec.read(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        GardenPieceCodec.write(tag, placement);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        int minX = Math.max(boundingBox.minX(), clip.minX());
        int maxX = Math.min(boundingBox.maxX(), clip.maxX());
        int minZ = Math.max(boundingBox.minZ(), clip.minZ());
        int maxZ = Math.min(boundingBox.maxZ(), clip.maxZ());
        int minY = Math.max(Math.max(boundingBox.minY(), clip.minY()), level.getMinBuildHeight());
        int maxY = Math.min(Math.min(boundingBox.maxY(), clip.maxY()), level.getMaxBuildHeight() - 1);
        // Resolve the palette once per slice, after block registration is complete.
        BlockState[] palette = palette();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material material = placement.at(x, y, z);
                    if (material == Material.KEEP) continue;
                    pos.set(x, y, z);
                    // Do not overwrite an unrelated structure's container or other block entity.
                    if (level.getBlockState(pos).hasBlockEntity()) continue;
                    level.setBlock(pos, palette[material.ordinal()], 2);
                }
            }
        }
    }

    private static BlockState[] palette() {
        BlockState[] states = new BlockState[Material.values().length];
        states[Material.AIR.ordinal()] = Blocks.AIR.defaultBlockState();
        states[Material.STONE.ordinal()] = RegisterBlock.HALL_ASH_SMOOTH_SANDSTONE.get().defaultBlockState();
        states[Material.TRIM.ordinal()] = RegisterBlock.HALL_ASH_CUT_SANDSTONE.get().defaultBlockState();
        states[Material.CARVED.ordinal()] = RegisterBlock.HALL_ASH_COLLAPSED_CHISELED_SANDSTONE.get().defaultBlockState();
        states[Material.DIRT.ordinal()] = RegisterBlock.HALL_DIRT.get().defaultBlockState();
        states[Material.GRASS.ordinal()] = RegisterBlock.HALL_GRASS_BLOCK.get().defaultBlockState();
        states[Material.LOG.ordinal()] = RegisterBlock.HALL_LOG.get().defaultBlockState();
        // HallWoodBlocks.Leaves extends Block: it has no vanilla leaf properties or decay logic.
        states[Material.LEAVES.ordinal()] = RegisterBlock.HALL_LEAVES.get().defaultBlockState();
        states[Material.DOMITE.ordinal()] = RegisterBlock.DOMITE_MINERAL.get().defaultBlockState();
        states[Material.DOMERITE.ordinal()] = RegisterBlock.DOMERITE_MINERAL.get().defaultBlockState();
        return states;
    }
}
