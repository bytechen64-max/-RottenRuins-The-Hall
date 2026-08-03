package org.bytechen.hall.overworld.swarm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.core.biome.BiomeSpreadHelper;

import java.util.*;
import java.util.concurrent.*;

/**
 * Hall 群系扩散处理器。
 * <p>
 * 使用异步队列处理群系变更请求，每 tick 处理固定数量的任务以保护 TPS。
 * 核心群系写入委托给 infcore 的 {@link BiomeSpreadHelper}。
 * 方块快照通过异步 IO 写入磁盘，用于净化（回退）操作。
 */
@SuppressWarnings("removal")
public final class HallBiomeProcessor {

    private static ServerLevel serverLevel;
    private static ExecutorService IO_EXECUTOR;

    private static final Set<BlockPos> PENDING_POS = ConcurrentHashMap.newKeySet();
    private static final Queue<SpreadTask> TASK_QUEUE = new ConcurrentLinkedQueue<>();
    private static final int QUEUE_HARD_LIMIT = 10000;
    private static final int MAX_PER_TICK = 40;

    private static HallDiskStorage storage;

    private static final Map<ChunkPos, Map<BlockPos, String>> CHUNK_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkPos, Map<BlockPos, String>> eldest) {
                    return size() > 2000;
                }
            }
    );

    private HallBiomeProcessor() {}

    // ==================== 线程池 ====================

    private static synchronized ExecutorService getExecutor() {
        if (IO_EXECUTOR == null || IO_EXECUTOR.isShutdown() || IO_EXECUTOR.isTerminated()) {
            IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Hall-IO");
                t.setDaemon(true);
                return t;
            });
        }
        return IO_EXECUTOR;
    }

    public static void initStorage(ServerLevel level) {
        if (storage == null) {
            String dimName = level.dimension().location().getPath();
            storage = new HallDiskStorage(
                    level.getServer().getWorldPath(LevelResource.ROOT)
                            .resolve("hall_data")
                            .resolve(dimName)
            );
        }
    }

    public static double getLoadFactor() {
        return (double) TASK_QUEUE.size() / QUEUE_HARD_LIMIT;
    }

    // ==================== 入队 ====================

    /**
     * 将方块位置入队以进行群系变更。
     */
    public static void enqueueForward(ServerLevel level, BlockPos pos, Holder<Biome> targetBiome,
                                       BlockState originalState) {
        initStorage(level);
        saveSnapshotAsync(pos, originalState);
        HallInfectionTracker.markChunkDirty(level.dimension(), pos);

        if (level.getBiome(pos).equals(targetBiome)) return;

        BlockPos immutablePos = pos.immutable();
        if (PENDING_POS.add(immutablePos)) {
            if (TASK_QUEUE.size() < QUEUE_HARD_LIMIT) {
                TASK_QUEUE.add(new SpreadTask(immutablePos, targetBiome, level.dimension()));
            } else {
                PENDING_POS.remove(immutablePos);
            }
        }
    }

    /**
     * 将方块位置入队以恢复原始群系（净化）。
     */
    public static BlockState enqueueReverse(ServerLevel level, BlockPos pos, BlockState fallbackState) {
        initStorage(level);
        BlockState original = getOriginalState(level, pos);

        BlockPos immutablePos = pos.immutable();
        if (PENDING_POS.add(immutablePos)) {
            Holder<Biome> defaultBiome = level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(Biomes.PLAINS);
            TASK_QUEUE.add(new SpreadTask(immutablePos, defaultBiome, level.dimension()));
        }

        return original != null ? original : fallbackState;
    }

    // ==================== 每 Tick 处理 ====================

    /**
     * 每 tick 从队列中取出并处理最多 MAX_PER_TICK 个群系变更任务。
     * 核心群系写入委托给 infcore 的 {@link BiomeSpreadHelper#setColumnQuartBiome}。
     */
    public static void processBatch(ServerLevel currentLevel) {
        serverLevel = currentLevel;
        if (TASK_QUEUE.isEmpty()) return;

        int processed = 0;
        Set<ChunkPos> dirtyChunks = new HashSet<>();

        while (!TASK_QUEUE.isEmpty() && processed < MAX_PER_TICK) {
            SpreadTask task = TASK_QUEUE.poll();
            if (task == null) continue;

            PENDING_POS.remove(task.pos());

            if (task.dimension().equals(currentLevel.dimension())) {
                // 使用 infcore 的快捷群系写入方法
                if (BiomeSpreadHelper.setColumnQuartBiome(currentLevel, task.pos(), task.targetBiome())) {
                    dirtyChunks.add(new ChunkPos(task.pos()));
                    processed++;
                }
            }
        }

        // 使用 infcore 的快捷区块同步方法
        for (ChunkPos cp : dirtyChunks) {
            BiomeSpreadHelper.syncChunk(currentLevel, cp);
        }
    }

    // ==================== 快照持久化 ====================

    private static void saveSnapshotAsync(BlockPos pos, BlockState state) {
        ExecutorService executor = getExecutor();
        if (executor.isShutdown()) return;

        ChunkPos cp = new ChunkPos(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        BlockPos immutablePos = pos.immutable();

        Map<BlockPos, String> data = CHUNK_CACHE.computeIfAbsent(cp,
                k -> new HashMap<>(storage.readChunk(cp)));

        if (!data.containsKey(immutablePos)) {
            data.put(immutablePos, blockId);
            try {
                executor.submit(() -> {
                    synchronized (storage) {
                        if (storage != null) {
                            storage.writeChunk(cp, data);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                HallMod.LOGGER.warn("[HallBiome] 快照任务提交被拒绝，可能正在关服");
            }
        }
    }

    public static BlockState getOriginalState(ServerLevel level, BlockPos pos) {
        initStorage(level);
        ChunkPos cp = new ChunkPos(pos);
        synchronized (CHUNK_CACHE) {
            Map<BlockPos, String> data = CHUNK_CACHE.computeIfAbsent(cp,
                    k -> new HashMap<>(storage.readChunk(cp)));
            String id = data.get(pos);
            if (id == null) return null;
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            return block != null ? block.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
    }

    // ==================== 生命周期 ====================

    public static void resetProcessor() {
        storage = null;
        CHUNK_CACHE.clear();
        PENDING_POS.clear();
        TASK_QUEUE.clear();
        serverLevel = null;
    }

    public static void forceFlushAll() {
        if (IO_EXECUTOR == null || IO_EXECUTOR.isShutdown()) return;

        HallMod.LOGGER.warn("[HallBiome] 正在强制保存 IO 任务并关闭线程池...");
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            resetProcessor();
        }
    }

    public static ServerLevel getServerLevel() { return serverLevel; }
    public static void setServerLevel(ServerLevel level) { serverLevel = level; }

    public record SpreadTask(BlockPos pos, Holder<Biome> targetBiome, ResourceKey<Level> dimension) {}
}
