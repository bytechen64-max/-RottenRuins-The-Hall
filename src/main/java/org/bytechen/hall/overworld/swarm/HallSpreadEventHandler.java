package org.bytechen.hall.overworld.swarm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.blocks.impl.HallVineBlock;
import org.bytechen.infcore.api.block.ISpreadBlock;
import org.bytechen.infcore.api.event.BlockSpreadEvent;
import org.bytechen.infcore.core.blockspread.BlockSpreadManager;

import java.util.*;

/**
 * Hall 感染群戏扩散事件处理器。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID)
public class HallSpreadEventHandler {

    /** 扩散后生成藤蔓的概率 */
    private static final float VINE_CHANCE = 0.3F;

    /** 树叶连锁扩散：每次扫描最大数量 */
    private static final int MAX_LEAF_CASCADE = 20;

    /** 树叶连锁扩散：总时间（tick） */
    private static final int LEAF_CASCADE_TICKS = 60;

    /** Hall 默认扩散类型 */
    private static final ResourceLocation HALL_SPREAD_TYPE =
            new ResourceLocation(HallMod.MODID, "spread");

    /** 可以被扩散的 vanilla 树叶集合（BFS 目标） */
    private static final Set<Block> VANILLA_LEAVES = Set.of(
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES
    );

    /** 每个维度的树叶连锁扩散队列 */
    private static final Map<ResourceLocation, LeafCascade> leafCascades = new HashMap<>();

    /**
     * 防止树叶连锁扩散时递归触发新的连锁。
     * <p>
     * cascade 内调 applySpread → 触发 BlockSpreadEvent.Post → onBlockSpread →
     * startLeafCascade → 重置计时器，形成死循环。此标记在 cascade 处理期间为 true，
     * startLeafCascade 检查到 true 则跳过。
     */
    private static final Set<ResourceLocation> cascadeInProgress = new HashSet<>();

    private HallSpreadEventHandler() {}

    // ==================== 方块扩散 → 群系变更 ====================

    @SubscribeEvent
    public static void onBlockSpread(BlockSpreadEvent.Post event) {
        if (!HALL_SPREAD_TYPE.equals(event.getSpreadType())) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Holder<Biome> hallBiome = resolveHallBiome(serverLevel);
        if (hallBiome == null) return;

        HallBiomeProcessor.enqueueForward(serverLevel, event.getPos(), hallBiome, event.getOldState());

        tryGrowVines(serverLevel, event.getPos(), event.getLevel().random);

        // 树叶被扩散后，连锁扩散相邻的所有 vanilla 树叶
        if (event.getNewState().is(RegisterBlock.HALL_LEAVES.get())) {
            startLeafCascade(serverLevel, event.getPos());
        }
    }

    // ==================== 树叶连锁扩散 ====================

    /**
     * BFS 扫描相邻 vanilla 树叶（最多 {@value MAX_LEAF_CASCADE} 个），
     * 入队后均匀分布在 {@value LEAF_CASCADE_TICKS} tick 内完成扩散。
     */
    private static void startLeafCascade(ServerLevel level, BlockPos origin) {
        ResourceLocation dim = level.dimension().location();

        // 防止连锁扩散自身的 applySpread 递归触发
        if (!cascadeInProgress.add(dim)) return;

        try {
            // BFS 收集所有相连的 vanilla 树叶
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(origin);
            visited.add(origin);

            List<BlockPos> found = new ArrayList<>();

            while (!queue.isEmpty() && found.size() < MAX_LEAF_CASCADE) {
                BlockPos pos = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    if (visited.add(neighbor)) {
                        BlockState state = level.getBlockState(neighbor);
                        if (VANILLA_LEAVES.contains(state.getBlock())) {
                            found.add(neighbor);
                            queue.add(neighbor);
                        } else if (state.is(RegisterBlock.HALL_LEAVES.get())) {
                            queue.add(neighbor);
                        }
                    }
                }
            }

            if (found.isEmpty()) return;

            LeafCascade cascade = leafCascades.computeIfAbsent(dim, k -> new LeafCascade());
            cascade.pending.addAll(found);
            cascade.ticksRemaining = LEAF_CASCADE_TICKS;
        } finally {
            cascadeInProgress.remove(dim);
        }
    }

    /** 每 tick 处理一部分树叶连锁扩散 */
    private static void tickLeafCascades(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        LeafCascade cascade = leafCascades.get(dim);
        if (cascade == null || cascade.pending.isEmpty()) return;

        int total = cascade.pending.size();
        int ticksLeft = Math.max(1, cascade.ticksRemaining);
        int batch = Math.max(1, (total + ticksLeft - 1) / ticksLeft);

        // 标记 cascade 处理中，防止 applySpread 触发的 Post 事件重新入队
        cascadeInProgress.add(dim);
        try {
            for (int i = 0; i < batch && !cascade.pending.isEmpty(); i++) {
                BlockPos pos = cascade.pending.poll();
                BlockState state = level.getBlockState(pos);
                if (VANILLA_LEAVES.contains(state.getBlock())) {
                    BlockSpreadManager.applySpread(level, pos, state, HALL_SPREAD_TYPE);
                }
            }
        } finally {
            cascadeInProgress.remove(dim);
        }

        cascade.ticksRemaining--;

        if (cascade.pending.isEmpty()) {
            leafCascades.remove(dim);
        }
    }

    /** 树叶连锁扩散队列条目 */
    private static class LeafCascade {
        final Deque<BlockPos> pending = new ArrayDeque<>();
        int ticksRemaining = LEAF_CASCADE_TICKS;
    }

    // ==================== 群系 ====================

    /**
     * 维度 → 扩散目标群系 的映射。
     * <p>
     * 未登记的维度回退到王庭群系。新增维度时只需在此登记一行，
     * 扩散管线（方块扩散 / 群系变更 / 快照）本身已按维度隔离。
     */
    private static final Map<ResourceKey<Level>, ResourceKey<Biome>> DIMENSION_TARGET_BIOME = Map.of(
            Level.OVERWORLD, ResourceKey.create(Registries.BIOME,
                    new ResourceLocation(HallMod.MODID, "hall_wasteland"))
    );

    /** 未登记维度的兜底目标群系 */
    private static final ResourceKey<Biome> DEFAULT_TARGET_BIOME = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(HallMod.MODID, "hall_wasteland"));

    @SuppressWarnings("deprecation")
    private static Holder<Biome> resolveHallBiome(ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);

        ResourceKey<Biome> biomeKey = DIMENSION_TARGET_BIOME.getOrDefault(
                level.dimension(), DEFAULT_TARGET_BIOME);

        var optional = registry.getHolder(biomeKey);
        if (optional.isPresent()) return optional.get();

        HallMod.LOGGER.warn("[HallBiome] 维度 {} 的目标群系 {} 未注册，回退到 plains",
                level.dimension().location(), biomeKey.location());
        return registry.getHolderOrThrow(Biomes.PLAINS);
    }

    // ==================== 扩散附带生成 ====================

    /** 扩散后生成植物的概率 */
    private static final float PLANT_CHANCE = 0.15F;

    private static void tryGrowVines(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (!(block instanceof ISpreadBlock spreadBlock)
                || !"hall".equals(spreadBlock.getSpreadType().getNamespace())) {
            return;
        }

        Block vine = RegisterBlock.HALL_VINE.get();
        BlockState vineDefault = vine.defaultBlockState();

        BlockPos abovePos = pos.above();
        BlockPos belowPos = pos.below();
        float r = random.nextFloat();

        if (level.isEmptyBlock(abovePos)) {
            if (r < VINE_CHANCE) {
                // 向上藤蔓
                level.setBlockAndUpdate(abovePos, vineDefault
                        .setValue(HallVineBlock.GROWTH_DIR, HallVineBlock.GrowthDirection.UP)
                        .setValue(HallVineBlock.SECTION, HallVineBlock.VineSection.TOP)
                        .setValue(HallVineBlock.AGE, 0));
            } else if (r < VINE_CHANCE + PLANT_CHANCE) {
                // 王庭草植物
                level.setBlockAndUpdate(abovePos, RegisterBlock.HALL_GRASS.get().defaultBlockState());
            }
        }

        if (level.isEmptyBlock(belowPos) && random.nextFloat() < VINE_CHANCE) {
            level.setBlockAndUpdate(belowPos, vineDefault
                    .setValue(HallVineBlock.GROWTH_DIR, HallVineBlock.GrowthDirection.DOWN)
                    .setValue(HallVineBlock.SECTION, HallVineBlock.VineSection.BOTTOM)
                    .setValue(HallVineBlock.AGE, 0));
        }
    }

    // ==================== Tick 驱动 ====================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END) {
            if (event.level instanceof ServerLevel serverLevel) {
                HallBiomeProcessor.processBatch(serverLevel);
                tickLeafCascades(serverLevel);
            }
        }
    }

    // ==================== 生命周期 ====================

    @SubscribeEvent
    public static void onServerStopping(ServerStoppedEvent event) {
        HallBiomeProcessor.forceFlushAll();
        HallInfectionTracker.clear();
        leafCascades.clear();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && !serverLevel.isClientSide) {
            // 快照数据与存储路径按维度隔离，每个维度都要初始化
            HallSpreadSaveData.get(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            HallInfectionTracker.clear(serverLevel.dimension());
            // 仅重置该维度的运行状态，不影响其它维度
            HallBiomeProcessor.resetDimension(serverLevel);
            leafCascades.remove(serverLevel.dimension().location());
        }
    }
}
