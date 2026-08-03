package org.bytechen.hall.overworld.swarm;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 感染区块追踪器。
 * <p>
 * 使用基于维度的脏区块集合，以 O(1) 复杂度快速判定
 * 某个坐标是否处于活跃感染区域内。
 */
public final class HallInfectionTracker {

    private static final Map<ResourceKey<Level>, LongSet> DIRTY_CHUNKS = new ConcurrentHashMap<>();

    private HallInfectionTracker() {}

    /**
     * 检查指定坐标所在的区块是否被标记为感染活跃。
     */
    public static boolean isChunkDirty(ResourceKey<Level> dim, BlockPos pos) {
        LongSet set = DIRTY_CHUNKS.get(dim);
        if (set == null || set.isEmpty()) return false;
        return set.contains(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /**
     * 检查指定坐标在默认维度（主世界）是否处于感染活跃区域。
     */
    public static boolean isPosActive(ResourceKey<Level> dim, BlockPos pos) {
        return isChunkDirty(dim, pos);
    }

    /**
     * 标记指定坐标所在的区块为感染活跃。
     */
    public static void markChunkDirty(ResourceKey<Level> dim, BlockPos pos) {
        DIRTY_CHUNKS.computeIfAbsent(dim, k -> new LongOpenHashSet())
                .add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /**
     * 标记指定区块坐标为感染活跃。
     */
    public static void markChunkDirty(ResourceKey<Level> dim, ChunkPos chunkPos) {
        DIRTY_CHUNKS.computeIfAbsent(dim, k -> new LongOpenHashSet())
                .add(chunkPos.toLong());
    }

    /**
     * 清除所有维度中的感染活跃标记。
     */
    public static void clear() {
        DIRTY_CHUNKS.clear();
    }

    /**
     * 清除指定维度的感染活跃标记。
     */
    public static void clear(ResourceKey<Level> dim) {
        LongSet set = DIRTY_CHUNKS.remove(dim);
        if (set != null) set.clear();
    }
}
