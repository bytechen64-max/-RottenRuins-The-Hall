package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

/**
 * 腐染矮草（从 corruption 工程 {@code CorruptionGrassBlock} 移植）。
 *
 * <p>十字草模型（datagen 生成 cutout cross）；只认腐染泥土 {@link CorruptionBlocks#SPREAD_DIRT}
 * 作为底面，不看光照、不看周围环境，下方泥土消失立刻消失。</p>
 */
public class CorruptionGrassBlock extends TallGrassBlock {
    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public CorruptionGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        // 核心：只认腐染泥土，不看光照，不看周围
        return below.is(CorruptionBlocks.SPREAD_DIRT.get());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
