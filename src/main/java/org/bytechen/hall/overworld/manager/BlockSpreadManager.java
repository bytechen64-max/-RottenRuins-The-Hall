package org.bytechen.hall.overworld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.api.block.ISpreadBlock;

/**
 * Hall 模组的方块扩散管理门面。
 * <p>
 * 实际扩散逻辑委托给 infcore 的 {@code org.bytechen.infcore.core.blockspread.BlockSpreadManager}，
 * 本类仅提供 Hall 模组专用的便捷封装和默认扩散类型常量。
 * <p>
 * 扩散规则由 {@link org.bytechen.hall.datagen.gen.BlockSpreadDataProvider} 在 datagen 阶段生成。
 */
public final class BlockSpreadManager {

    /** 本模组的默认扩散类型 */
    public static final ResourceLocation DEFAULT_TYPE =
            new ResourceLocation(HallMod.MODID, "spread");

    private BlockSpreadManager() {}

    /**
     * 使用默认扩散类型对方块应用扩散。
     *
     * @param level 世界
     * @param pos   目标位置
     * @return 是否成功扩散
     */
    public static boolean applySpread(Level level, BlockPos pos) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.applySpread(level, pos, DEFAULT_TYPE);
    }

    /**
     * 使用指定扩散类型对方块应用扩散。
     */
    public static boolean applySpread(Level level, BlockPos pos, ResourceLocation type) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.applySpread(level, pos, type);
    }

    /**
     * 使用指定扩散类型对方块状态应用扩散。
     */
    public static boolean applySpread(Level level, BlockPos pos, BlockState state, ResourceLocation type) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.applySpread(level, pos, state, type);
    }

    /**
     * 检查方块是否有默认扩散类型的规则。
     */
    public static boolean hasSpreadRule(BlockState state) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.hasSpreadRule(state, DEFAULT_TYPE);
    }

    /**
     * 检查方块是否有指定扩散类型的规则。
     */
    public static boolean hasSpreadRule(BlockState state, ResourceLocation type) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.hasSpreadRule(state, type);
    }

    /**
     * 检查方块（按 Block 实例）是否有默认扩散规则。
     */
    public static boolean hasSpreadRule(Block block) {
        return org.bytechen.infcore.core.blockspread.BlockSpreadManager.hasSpreadRule(block, DEFAULT_TYPE);
    }

    /**
     * 对指定位置应用默认扩散类型的扩散。
     * 如果该位置的 Block 实现了 {@link ISpreadBlock}，自动使用其扩散类型。
     *
     * @param level 世界
     * @param pos   目标位置
     * @return 是否成功扩散
     */
    public static boolean applySpreadAuto(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ISpreadBlock sb) {
            return org.bytechen.infcore.core.blockspread.BlockSpreadManager.applySpread(level, pos, state, sb.getSpreadType());
        }
        return applySpread(level, pos);
    }
}
