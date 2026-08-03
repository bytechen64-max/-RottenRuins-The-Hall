package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.overworld.registry.RegisterBlock;
import org.bytechen.hall.overworld.registry.blocks.base.AbstractSpreadBlock;

/**
 * 默认的扩散方块实现。
 * <p>
 * 特性：
 * <ul>
 *   <li>使用 infcore 的 {@code BlockSpreadManager} 进行方块转换（规则由 datagen 生成）</li>
 *   <li>草方块变种：上方被遮挡时会退化为泥土变种</li>
 * </ul>
 */
public class SpreadBlock extends AbstractSpreadBlock {

    public SpreadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canSurvive(LevelReader level, BlockPos pos, BlockState state) {
        // 草方块需要上方有光照（不被遮挡），否则退化为泥土
        if (state.is(RegisterBlock.HALL_GRASS_BLOCK.get())) {
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (aboveState.isSolidRender(level, abovePos) && aboveState.isCollisionShapeFullBlock(level, abovePos)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected BlockState getDegradedState(BlockState state, ServerLevel level, BlockPos pos) {
        // hall_grass → hall_dirt
        if (state.is(RegisterBlock.HALL_GRASS_BLOCK.get())) {
            Block hallDirt = RegisterBlock.HALL_DIRT.get();
            if (hallDirt != null) {
                return hallDirt.defaultBlockState();
            }
        }
        // hall_dirt → dirt (彻底净化)
        if (state.is(RegisterBlock.HALL_DIRT.get())) {
            return Blocks.DIRT.defaultBlockState();
        }
        return null;
    }
}
