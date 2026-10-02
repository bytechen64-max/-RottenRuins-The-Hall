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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hall 群系扩散处理器。
 * <p>
 * 使用异步队列处理群系变更请求，每 tick 处理固定数量的任务以保护 TPS。
 * 核心群系写入委托给 infcore 的 {@link BiomeSpreadHelper}。
 * 方块快照通过异步 IO 写入磁盘，用于净化（回退）操作。
 *
 * <h2>多维度隔离</h2>
 * 本处理器为<b>每个维度维护独立的运行状态</b>（{@link DimensionState}）：
 * 快照存储、待处理坐标集合、任务队列、区块缓存均互不共享。
 * <p>
 * 这一点是必需的：不同维度会存在<b>完全相同的方块坐标</b>，
 * 若共用一套 PENDING_POS / CHUNK_CACHE，主世界与王庭之外的维度会互相
 * 覆盖快照，导致净化（回退）时把 A 维度的原始方块还原到 B 维度。
 * 同理，任务队列必须按维度隔离，否则 {@code processBatch} 在弹出
 * 其它维度的任务后会直接丢弃它们（旧实现即为此 bug）。
 */
@SuppressWarnings("removal")
public final class HallBiomeProcessor {

    /** 每个维度的独立运行状态 */
    private static final Map<ResourceKey<Level>, DimensionState> STATES = new ConcurrentHashMap<>();

    /** 快照 IO 线程池（全局共享，单线程串行写入） */
    private static ExecutorService IO_EXECUTOR;

    private static final int QUEUE_HARD_LIMIT = 10000;
    private static final int MAX_PER_TICK = 40;

    /** 每个维度缓存的最大区块数 */
    private static final int MAX_CACHED_CHUNKS = 2000;

    private HallBiomeProcessor() {}

    // ==================== 维度状态 ====================

    /**
     * 单个维度的扩散运行状态。
     * <p>
     * 所有字段都是维度私有的，构造时即为该维度创建独立的磁盘存储目录。
     */
    private static final class DimensionState {

        final ResourceKey<Level> dimension;

        /** 该维度的方块快照存储 */
        final HallDiskStorage storage;

        /** 该维度中已入队、尚未处理的坐标（用于去重） */
        final Set<BlockPos> pendingPos = ConcurrentHashMap.newKeySet();

        /** 该维度的群系变更任务队列 */
        final Queue<SpreadTask> taskQueue = new ConcurrentLinkedQueue<>();

        /** 该维度的区块快照缓存（LRU） */
        final Map<ChunkPos, Map<BlockPos, String>> chunkCache;

        DimensionState(ServerLevel level) {
            this.dimension = level.dimension();
            this.storage = new HallDiskStorage(resolveDimensionDir(level));
            this.chunkCache = Collections.synchronizedMap(
                    new LinkedHashMap<>(256, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<ChunkPos, Map<BlockPos, String>> eldest) {
                            return size() > MAX_CACHED_CHUNKS;
                        }
                    }
            );
        }
    }

    /**
     * 解析维度对应的数据目录：{@code <world>/hall_data/<namespace>_<path>}。
     * <p>
     * 旧版本使用裸的 {@code getPath()}（如 {@code hall_data/overworld}），
     * 命名空间不同的两个模组可能撞名。此处保留裸路径目录的兼容迁移。
     */
    private static Path resolveDimensionDir(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).resolve("hall_data");
        ResourceLocation dimId = level.dimension().location();
        String flatName = dimId.getNamespace() + "_" + dimId.getPath();

        Path current = root.resolve(flatName);
        Path legacy = root.resolve(dimId.getPath());

        // 旧目录存在且新目录尚未建立时，迁移一次，避免老存档丢失快照
        if (Files.isDirectory(legacy) && !Files.exists(current)) {
            try {
                Files.move(legacy, current);
                HallMod.LOGGER.info("[HallBiome] 已迁移旧快照目录 {} -> {}",
                        legacy.getFileName(), current.getFileName());
            } catch (IOException e) {
                HallMod.LOGGER.warn("[HallBiome] 旧快照目录迁移失败，回退到使用旧目录: {}", legacy, e);
                return legacy;
            }
        }
        return current;
    }

    /** 取得（必要时创建）指定维度的运行状态 */
    private static DimensionState state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new DimensionState(level));
    }

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

    /**
     * 初始化指定维度的存储路径与线程池。
     * <p>
     * 幂等：同一维度重复调用不会重建存储。
     */
    public static void initStorage(ServerLevel level) {
        state(level);
        getExecutor();
    }

    /** 该维度当前的队列负载（0.0 ~ 1.0+），用于节流判定 */
    public static double getLoadFactor(ServerLevel level) {
        DimensionState st = STATES.get(level.dimension());
        if (st == null) return 0.0D;
        return (double) st.taskQueue.size() / QUEUE_HARD_LIMIT;
    }

    /** 该维度当前的队列负载（取所有维度中的最大值） */
    public static double getLoadFactor() {
        int max = 0;
        for (DimensionState st : STATES.values()) {
            max = Math.max(max, st.taskQueue.size());
        }
        return (double) max / QUEUE_HARD_LIMIT;
    }

    // ==================== 入队 ====================

    /**
     * 将方块位置入队以进行群系变更。
     */
    public static void enqueueForward(ServerLevel level, BlockPos pos, Holder<Biome> targetBiome,
                                      BlockState originalState) {
        DimensionState st = state(level);
        getExecutor();
        saveSnapshotAsync(st, pos, originalState);
        HallInfectionTracker.markChunkDirty(level.dimension(), pos);

        if (level.getBiome(pos).equals(targetBiome)) return;

        BlockPos immutablePos = pos.immutable();
        if (st.pendingPos.add(immutablePos)) {
            if (st.taskQueue.size() < QUEUE_HARD_LIMIT) {
                st.taskQueue.add(new SpreadTask(immutablePos, targetBiome, level.dimension()));
            } else {
                st.pendingPos.remove(immutablePos);
            }
        }
    }

    /**
     * 将方块位置入队以恢复原始群系（净化）。
     */
    public static BlockState enqueueReverse(ServerLevel level, BlockPos pos, BlockState fallbackState) {
        DimensionState st = state(level);
        BlockState original = getOriginalState(level, pos);

        BlockPos immutablePos = pos.immutable();
        if (st.pendingPos.add(immutablePos)) {
            Holder<Biome> defaultBiome = level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(Biomes.PLAINS);
            st.taskQueue.add(new SpreadTask(immutablePos, defaultBiome, level.dimension()));
        }

        return original != null ? original : fallbackState;
    }

    // ==================== 每 Tick 处理 ====================

    /**
     * 每 tick 从<b>该维度自己的</b>队列中取出并处理最多 {@value MAX_PER_TICK} 个群系变更任务。
     * <p>
     * 核心群系写入委托给 infcore 的 {@link BiomeSpreadHelper#setColumnQuartBiome}。
     * 队列按维度隔离，因此这里无需（也不可能）误弹其它维度的任务。
     */
    public static void processBatch(ServerLevel currentLevel) {
        DimensionState st = state(currentLevel);
        if (st.taskQueue.isEmpty()) return;

        int processed = 0;
        Set<ChunkPos> dirtyChunks = new HashSet<>();

        while (!st.taskQueue.isEmpty() && processed < MAX_PER_TICK) {
            SpreadTask task = st.taskQueue.poll();
            if (task == null) continue;

            st.pendingPos.remove(task.pos());

            // 队列已按维度隔离，此处仅作防御性校验
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

    private static void saveSnapshotAsync(DimensionState st, BlockPos pos, BlockState state) {
        ExecutorService executor = getExecutor();
        if (executor.isShutdown()) return;

        ChunkPos cp = new ChunkPos(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        BlockPos immutablePos = pos.immutable();

        Map<BlockPos, String> data = st.chunkCache.computeIfAbsent(cp,
                k -> new HashMap<>(st.storage.readChunk(cp)));

        if (!data.containsKey(immutablePos)) {
            data.put(immutablePos, blockId);
            try {
                executor.submit(() -> {
                    // 快照 Map 在入队后仍可能被并发写入，故写盘时对 storage 加锁
                    synchronized (st.storage) {
                        st.storage.writeChunk(cp, data);
                    }
                });
            } catch (RejectedExecutionException e) {
                HallMod.LOGGER.warn("[HallBiome] 快照任务提交被拒绝，可能正在关服");
            }
        }
    }

    public static BlockState getOriginalState(ServerLevel level, BlockPos pos) {
        DimensionState st = state(level);
        ChunkPos cp = new ChunkPos(pos);
        synchronized (st.chunkCache) {
            Map<BlockPos, String> data = st.chunkCache.computeIfAbsent(cp,
                    k -> new HashMap<>(st.storage.readChunk(cp)));
            String id = data.get(pos);
            if (id == null) return null;
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            return block != null ? block.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
    }

    // ==================== 生命周期 ====================

    /** 清空指定维度的运行状态（不影响其它维度与磁盘数据） */
    public static void resetDimension(ServerLevel level) {
        DimensionState st = STATES.remove(level.dimension());
        if (st != null) {
            st.chunkCache.clear();
            st.pendingPos.clear();
            st.taskQueue.clear();
        }
    }

    /** 清空所有维度的运行状态 */
    public static void resetProcessor() {
        STATES.clear();
    }

    /** 强制刷盘并关闭 IO 线程池（关服时调用） */
    public static void forceFlushAll() {
        if (IO_EXECUTOR == null || IO_EXECUTOR.isShutdown()) {
            resetProcessor();
            return;
        }

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

    public record SpreadTask(BlockPos pos, Holder<Biome> targetBiome, ResourceKey<Level> dimension) {}
}
