package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** FACING 从支撑面指向桩头，模型的 Y=0 面始终贴住支撑方块。 */
public final class GroundingStakeBlock extends DirectionalBlock {
    public GroundingStakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return !(support.getBlock() instanceof GroundingStakeBlock)
                && support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> Shapes.or(box(7, 0, 7, 9, 8, 9), box(6.5, 8, 6.5, 9.5, 11, 9.5));
            case DOWN -> Shapes.or(box(7, 8, 7, 9, 16, 9), box(6.5, 5, 6.5, 9.5, 8, 9.5));
            case EAST -> Shapes.or(box(0, 7, 7, 8, 9, 9), box(8, 6.5, 6.5, 11, 9.5, 9.5));
            case WEST -> Shapes.or(box(8, 7, 7, 16, 9, 9), box(5, 6.5, 6.5, 8, 9.5, 9.5));
            case SOUTH -> Shapes.or(box(7, 7, 0, 9, 9, 8), box(6.5, 6.5, 8, 9.5, 9.5, 11));
            case NORTH -> Shapes.or(box(7, 7, 8, 9, 9, 16), box(6.5, 6.5, 5, 9.5, 9.5, 8));
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
