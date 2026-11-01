package org.bytechen.rottenruins.overworld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 腐化群系区域数据（每个 {@link ServerLevel} 独立）。
 *
 * <p>记录由一阶召唤柱/腐化扩散建立的腐化区域中心与半径；即使召唤柱被拆掉，
 * 区域仍然保留，腐化生物会继续在区域内自然生成。</p>
 */
public class CorruptionZoneData extends SavedData {

    public static final String DATA_NAME = "rotten_ruins_corruption_zones";
    /** 合并距离：两个区域内中心距离小于该值时合并 */
    private static final double MERGE_DISTANCE = 12.0;
    private static final int MAX_RADIUS = 16;

    public static final class Zone {
        public final BlockPos center;
        public int radius;

        public Zone(BlockPos center, int radius) {
            this.center = center;
            this.radius = radius;
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    public static CorruptionZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CorruptionZoneData::load, CorruptionZoneData::new, DATA_NAME);
    }

    public List<Zone> getZones() {
        return zones;
    }

    /** 添加/扩张一个腐化区域。 */
    public void addZone(BlockPos center, int radius) {
        for (Zone zone : zones) {
            if (zone.center.distSqr(center) <= MERGE_DISTANCE * MERGE_DISTANCE) {
                zone.radius = Math.min(MAX_RADIUS, Math.max(zone.radius, radius));
                setDirty();
                return;
            }
        }
        zones.add(new Zone(center, Math.min(MAX_RADIUS, radius)));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", zone.center.asLong());
            entry.putInt("Radius", zone.radius);
            list.add(entry);
        }
        tag.put("Zones", list);
        return tag;
    }

    public static CorruptionZoneData load(CompoundTag tag) {
        CorruptionZoneData data = new CorruptionZoneData();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.zones.add(new Zone(BlockPos.of(entry.getLong("Pos")), entry.getInt("Radius")));
        }
        return data;
    }
}
