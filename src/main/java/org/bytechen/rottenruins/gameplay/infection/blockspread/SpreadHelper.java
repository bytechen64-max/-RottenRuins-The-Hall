package org.bytechen.rottenruins.gameplay.infection.blockspread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.api.infection.block.ISpreadBlock;

/**
 * 方块扩散快捷工具。
 * <p>
 * 提供方块向邻近方块扩散的常用范式，让模组的 spread block 实现更简洁。
 * <p>
 * 典型用法（在 {@code randomTick} 中）：
 * <pre>{@code
 * if (this instanceof ISpreadBlock spreadBlock) {
 *     SpreadHelper.trySpreadNearby(state, level, pos, random, spreadBlock, 3, 0.5f);
 * }
 * }</pre>
 */
public final class SpreadHelper {

    private SpreadHelper() {}

    /**
     * 尝试向邻近方块扩散若干次。
     * <p>
     * 每次尝试随机选择一个方向，检查目标方块是否有该类型的扩散规则，
     * 如果有则调用 {@link BlockSpreadManager#applySpread} 执行扩散。
     *
     * @param state      当前方块状态
     * @param level      服务器世界
     * @param pos        当前方块位置
     * @param random     随机源
     * @param spreadBlock 扩散方块（提供扩散类型）
     * @param attempts   每 tick 尝试次数
     * @param chance     每次尝试的基础概率 (0.0 ~ 1.0)
     * @return 成功扩散的次数
     */
    public static int trySpreadNearby(BlockState state, ServerLevel level, BlockPos pos,
                                       RandomSource random, ISpreadBlock spreadBlock,
                                       int attempts, float chance) {
        int successCount = 0;
        for (int i = 0; i < attempts; i++) {
            if (random.nextFloat() >= chance) continue;

            Direction dir = Direction.getRandom(random);
            BlockPos targetPos = pos.relative(dir);

            if (!level.isLoaded(targetPos)) continue;

            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir()) continue;
            if (targetState.is(state.getBlock())) continue;

            if (BlockSpreadManager.applySpread(level, targetPos, targetState, spreadBlock.getSpreadType())) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 检查目标方块是否可被扩散。
     * <p>
     * 等同于：非空气 && 非同种方块 && 存在扩散规则。
     *
     * @param level      世界（只读）
     * @param targetPos  目标位置
     * @param targetState 目标方块状态
     * @param spreadBlock 扩散方块
     * @return true 如果该位置可以被扩散
     */
    public static boolean canSpreadTo(LevelReader level, BlockPos targetPos, BlockState targetState,
                                       ISpreadBlock spreadBlock) {
        if (targetState.isAir()) return false;
        return BlockSpreadManager.hasSpreadRule(targetState, spreadBlock.getSpreadType());
    }
}
