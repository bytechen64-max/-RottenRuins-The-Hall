package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

/**
 * 腐染矮草（两格高）。
 *
 * <p>使用 {@link DoublePlantBlock} 的半格机制：下半格只能长在腐染泥土上，
 * 上半格必须踩在下半格上；下半格随机 tick 时若上方是空气，有概率长出上半格，
 * 所以腐化区域里会同时看到一格高与两格高的草。</p>
 */
public class CorruptionGrassBlock extends DoublePlantBlock {

    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    /** 下半格长成两格高的概率（每次随机 tick） */
    private static final float GROW_TALL_CHANCE = 0.12F;

    public CorruptionGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        // 下半格只认腐染泥土，不看光照
        return level.getBlockState(pos.below()).is(CorruptionBlocks.SPREAD_DIRT.get());
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        if (level.getBlockState(pos.above()).isAir() && random.nextFloat() < GROW_TALL_CHANCE) {
            DoublePlantBlock.placeAt(level, this.defaultBlockState(), pos, 3);
        }
    }
}
