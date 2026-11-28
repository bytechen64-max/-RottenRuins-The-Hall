package org.bytechen.rottenruins.network.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

/** One burst instruction; clients create ordinary vanilla dust with explicit upward velocity. */
public final class FleshbedRitualParticles {
    public static final String ACTION = "fleshbed_dust_burst";
    private FleshbedRitualParticles() {}

    public static void send(ServerLevel level, BlockPos pos, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putInt("count", Math.max(0, Math.min(256, count)));
        tag.putString("dimension", level.dimension().location().toString());
        NetworkHelper.sendToNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }
}
