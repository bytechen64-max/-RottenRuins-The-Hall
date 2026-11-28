package org.bytechen.rottenruins.content.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Kind;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Placement;

import java.util.Locale;
import java.util.Optional;

/** Large static landmarks, assembled through vanilla's structure/chunk lifecycle. */
public final class GardenMonumentStructure extends Structure {
    public static final Codec<GardenMonumentStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            Codec.STRING.comapFlatMap(GardenMonumentStructure::decodeKind,
                    kind -> kind.name().toLowerCase(Locale.ROOT)).fieldOf("monument")
                    .forGetter(structure -> structure.kind)
    ).apply(instance, GardenMonumentStructure::new));
    public static final StructureType<GardenMonumentStructure> TYPE = () -> CODEC;
    private final Kind kind;

    public GardenMonumentStructure(StructureSettings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    private static DataResult<Kind> decodeKind(String name) {
        try {
            return DataResult.success(Kind.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown garden monument: " + name);
        }
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int sea = context.chunkGenerator().getSeaLevel();
        int surface = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        int baseY;
        if (kind == Kind.EMPTY_THRONE) {
            if (surface <= sea + 8) return Optional.empty();
            baseY = surface - 12;
            // Reject cliffs and coastlines rather than flattening a large area of terrain.
            for (int dx : new int[]{-32, 32}) for (int dz : new int[]{-32, 32}) {
                int corner = height(context, x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG);
                if (corner <= sea + 4 || Math.abs(corner - surface) > 22) return Optional.empty();
            }
        } else {
            baseY = sea - 36;
            for (int dx : new int[]{-64, 0, 64}) for (int dz : new int[]{-64, 0, 64}) {
                int floor = height(context, x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG);
                // The buried lower band must meet the seabed; tips remain above the water.
                if (floor > sea - 16 || floor < baseY + kind.minY) return Optional.empty();
            }
        }
        if (baseY + kind.minY < context.heightAccessor().getMinBuildHeight()
                || baseY + kind.maxY >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        Placement placement = new Placement(kind, x, baseY, z, context.random().nextInt(4));
        return Optional.of(new GenerationStub(new BlockPos(x, surface, z), builder -> addSlices(builder, placement)));
    }

    private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, type,
                context.heightAccessor(), context.randomState());
    }

    private static void addSlices(StructurePiecesBuilder builder, Placement placement) {
        for (var slice : placement.slices()) {
            builder.addPiece(new GardenMonumentPiece(placement, new BoundingBox(
                    slice.minX(), placement.baseY() + placement.kind().minY, slice.minZ(),
                    slice.maxX(), placement.baseY() + placement.kind().maxY, slice.maxZ())));
        }
    }

    @Override
    public StructureType<?> type() { return TYPE; }
}
