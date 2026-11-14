package org.bytechen.rottenruins.network.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

import java.util.Collection;

/** One bounded batch per field beat, including fallback positions for lethal hits. */
public final class OlympiadImpactPacket {
    public static final String ACTION = "olympiad_impact";
    public static final int MAX_HITS = 64;

    private OlympiadImpactPacket() {}

    public static CompoundTag snapshot(LivingEntity victim) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("entity", victim.getId());
        tag.putUUID("uuid", victim.getUUID());
        Vec3 center = victim.getBoundingBox().getCenter();
        tag.putDouble("x", center.x);
        tag.putDouble("y", center.y);
        tag.putDouble("z", center.z);
        tag.putFloat("size", Math.max(0.18f, Math.min(0.7f, victim.getBbWidth() * 0.45f)));
        return tag;
    }

    public static void send(ServerLevel level, Vec3 origin, Collection<CompoundTag> hits) {
        if (hits.isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        hits.stream().limit(MAX_HITS).forEach(entries::add);
        tag.put("hits", entries);
        tag.putString("dimension", level.dimension().location().toString());
        NetworkHelper.sendToNear(level, origin.x, origin.y, origin.z, 64.0d,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }
}
