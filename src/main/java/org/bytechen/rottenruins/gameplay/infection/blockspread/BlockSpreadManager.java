package org.bytechen.rottenruins.gameplay.infection.blockspread;
import org.bytechen.rottenruins.core.infection.rules.WeightedSelection;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadTarget;
import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadEntry;

import org.bytechen.rottenruins.utils.logging.InfectionLog;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.gameplay.infection.InfectionRuntime;
import org.bytechen.rottenruins.api.infection.block.ISpreadBlock;
import org.bytechen.rottenruins.api.infection.event.BlockSpreadEvent;
import org.bytechen.rottenruins.utils.logging.ThrottledLogger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import org.bytechen.rottenruins.core.infection.rules.InfectionRules;

/**
 * 方块扩散管理器。
 * <p>
 * 统一管理所有方块扩散规则，与 {@code EvolutionManager} 共用服务端资源重载后的规则快照。
 * 每条规则通过 {@code type} 字段区分扩散类型。
 * <p>
 * 数据文件路径：{@code data/<namespace>/infcore_blockspread/*.json}
 * <p>
 * 主要 API：
 * <ul>
 *   <li>{@link #applySpread(Level, BlockPos, ResourceLocation)} —— 对方块应用指定类型的扩散</li>
 *   <li>{@link #applySpread(Level, BlockPos, BlockState, ResourceLocation)} —— 对方块状态应用扩散</li>
 *   <li>{@link #applySpread(Level, BlockPos, ISpreadBlock)} —— 对实现 ISpreadBlock 的方块自动获取类型</li>
 *   <li>{@link #hasSpreadRule(BlockState, ResourceLocation)} —— 检查方块状态是否存在扩散规则</li>
 *   <li>{@link #hasSpreadRule(Block, ResourceLocation)} —— 检查方块是否存在扩散规则</li>
 * </ul>
 */
public final class BlockSpreadManager {


    /**
     * 扩散明细日志节流器：逐方块明细默认进 TRACE，每 30 秒最多汇总一条 DEBUG。
     * 感染爆发时扩散每秒可达上百次，逐条打印会刷爆 debug.log。
     */
    private static final ThrottledLogger SPREAD_LOG =
            new ThrottledLogger(InfectionLog.LOGGER, "BlockSpreadManager");

    private BlockSpreadManager() {}

    // ==================== 公共 API ====================

    /**
     * 对方块应用指定类型的扩散。
     *
     * @param level 世界
     * @param pos   要扩散的方块位置
     * @param type  扩散类型的 ResourceLocation（如 {@code hall:spread}）
     * @return 是否成功扩散
     */
    public static boolean applySpread(Level level, BlockPos pos, ResourceLocation type) {
        if (level == null || pos == null || type == null) return false;
        return doSpread(level, pos, type);
    }

    /**
     * 对方块状态应用指定类型的扩散。避免重复获取 BlockState。
     */
    public static boolean applySpread(Level level, BlockPos pos, BlockState state, ResourceLocation type) {
        if (level == null || pos == null || state == null || type == null) return false;
        return doSpread(level, pos, state, type);
    }

    /**
     * 对实现 {@link ISpreadBlock} 接口的方块应用扩散。
     * 自动从方块实例获取扩散类型，无需手动指定。
     *
     * @param level 世界
     * @param pos   要扩散的方块位置
     * @param block 实现了 ISpreadBlock 的方块实例
     * @return 是否成功扩散
     */
    public static boolean applySpread(Level level, BlockPos pos, ISpreadBlock block) {
        if (level == null || pos == null || block == null) return false;
        return applySpread(level, pos, block.getSpreadType());
    }

    /**
     * 检查方块状态是否存在指定类型的扩散规则。
     */
    public static boolean hasSpreadRule(BlockState state, ResourceLocation type) {
        if (state == null || type == null) return false;
        return hasSpreadRule(state.getBlock(), type);
    }

    /**
     * 检查方块是否存在指定类型的扩散规则。
     */
    public static boolean hasSpreadRule(Block block, ResourceLocation type) {
        if (block == null || type == null) return false;
        return InfectionRules.current().spread().get(type.toString(),
                InfectionRuntime.services().blockId(block).toString()) != null;
    }

    /**
     * 检查方块状态是否存在任意由 ISpreadBlock 定义的扩散规则。
     * 当 state 的 Block 实现了 ISpreadBlock 时，使用其自身类型查询。
     */
    public static boolean hasSpreadRule(BlockState state) {
        if (state == null) return false;
        if (state.getBlock() instanceof ISpreadBlock spreadBlock) {
            return hasSpreadRule(state, spreadBlock.getSpreadType());
        }
        return false;
    }

    /**
     * 获取指定方块和扩散类型的可能目标列表。
     */
    @Nullable
    public static List<BlockSpreadTarget> getSpreadTargets(Block block, ResourceLocation type) {
        if (block == null || type == null) return null;
        BlockSpreadEntry entry = InfectionRules.current().spread().get(type.toString(),
                InfectionRuntime.services().blockId(block).toString());
        return entry != null ? entry.results() : null;
    }

    /**
     * 获取已加载的扩散条目总数（用于调试）。
     */
    public static int getEntryCount() {
        return InfectionRules.current().spread().size();
    }

    // ==================== 配置重载 ====================

    /**
     * 从当前服务端资源重新加载感染与扩散规则；包含已启用的数据包。
     */
    public static void forceReload() {
        InfectionRules.reload();
    }

    // ==================== 内部核心 ====================

    private static boolean doSpread(Level level, BlockPos pos, ResourceLocation type) {
        BlockState state = level.getBlockState(pos);
        return doSpread(level, pos, state, type);
    }

    private static boolean doSpread(Level level, BlockPos pos, BlockState state, ResourceLocation type) {
        if (state.isAir()) return false;

        ResourceLocation sourceKey = InfectionRuntime.services().blockId(state.getBlock());

        BlockSpreadEntry entry = InfectionRules.current().spread().get(type.toString(), sourceKey.toString());
        if (entry == null || entry.results() == null || entry.results().isEmpty()) {
            return false;
        }

        BlockSpreadTarget chosen = WeightedSelection.pick(entry.results(), BlockSpreadTarget::weight, level.random::nextInt);
        if (chosen == null) return false;

        ResourceLocation targetKey = ResourceLocation.tryParse(chosen.target());
        if (targetKey == null) return false;

        Block targetBlock = InfectionRuntime.services().block(targetKey);
        if (targetBlock == null) {
            InfectionLog.LOGGER.warn("BlockSpreadManager: unknown target block type '{}'", chosen.target());
            return false;
        }

        BlockState targetState = targetBlock.defaultBlockState();

        // 扩散前事件（可取消）
        BlockSpreadEvent.Pre preEvent = new BlockSpreadEvent.Pre(level, pos, state, targetState, type);
        if (InfectionRuntime.services().beforeSpread(preEvent)) {
            SPREAD_LOG.record("spread cancelled by event at {}", pos);
            return false;
        }

        // 根据配置决定是否掉落资源
        if (entry.dropResources()) {
            Block.dropResources(state, level, pos, null, null, ItemStack.EMPTY);
        }

        level.setBlock(pos, targetState, Block.UPDATE_ALL_IMMEDIATE);

        // 扩散后事件（不可取消）
        InfectionRuntime.services().afterSpread(new BlockSpreadEvent.Post(level, pos, state, targetState, type));

        SPREAD_LOG.record("'{}' spread to '{}' at {} (type={})",
                sourceKey, chosen.target(), pos, type);
        return true;
    }

}
