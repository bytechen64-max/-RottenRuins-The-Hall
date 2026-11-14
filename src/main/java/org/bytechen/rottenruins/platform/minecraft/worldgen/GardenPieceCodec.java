package org.bytechen.rottenruins.platform.minecraft.worldgen;

import net.minecraft.nbt.CompoundTag;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Kind;
import org.bytechen.rottenruins.core.worldgen.GardenMonument.Placement;

/** Version-specific structure-piece payload. Vanilla owns the piece ID and bounding box. */
public final class GardenPieceCodec {
    private GardenPieceCodec() {}

    public static Placement read(CompoundTag tag) {
        return new Placement(Kind.valueOf(tag.getString("Monument")), tag.getInt("CenterX"),
                tag.getInt("BaseY"), tag.getInt("CenterZ"), tag.getInt("Turns"));
    }

    public static void write(CompoundTag tag, Placement placement) {
        tag.putString("Monument", placement.kind().name());
        tag.putInt("CenterX", placement.centerX());
        tag.putInt("BaseY", placement.baseY());
        tag.putInt("CenterZ", placement.centerZ());
        tag.putInt("Turns", placement.turns());
    }
}
