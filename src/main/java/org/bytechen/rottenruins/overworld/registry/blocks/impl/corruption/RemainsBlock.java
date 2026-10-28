package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 残骸方块基类（从 corruption 工程 {@code Remains} 移植）。
 *
 * <p>六向转角：贴着墙 / 天花板 / 地板放置，碰撞箱按朝向贴附在对应面，玩家可以穿过
 * （{@link #getCollisionShape} 返回空）。模型本身是十字交叉平面（由 datagen 的
 * {@code directionComplexBlockWithItem} 生成），blockstate 按 {@code facing} 旋转模型。</p>
 */
public class RemainsBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // 修正后的碰撞箱定义 - 确保每个方向都正确对应
    private static final VoxelShape SHAPE_DOWN = Block.box(0.8D, 12.0D, 0.8D, 15.2D, 16.0D, 15.2D);
    private static final VoxelShape SHAPE_UP = Block.box(0.8D, 0.0D, 0.8D, 15.2D, 4.0D, 15.2D);
    private static final VoxelShape SHAPE_NORTH = Block.box(0.8D, 0.8D, 12.0D, 15.2D, 15.2D, 16.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.8D, 0.8D, 0.0D, 15.2D, 15.2D, 4.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(12.0D, 0.8D, 0.8D, 16.0D, 15.2D, 15.2D);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0D, 0.8D, 0.8D, 4.0D, 15.2D, 15.2D);

    public RemainsBlock() {
        super(Properties.of()
                .sound(SoundType.GRASS)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        LevelReader level = context.getLevel();

        BlockState state = this.defaultBlockState().setValue(FACING, direction);
        if (state.canSurvive(level, pos)) {
            return state;
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // 玩家可以穿过
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        BlockState attachedState = world.getBlockState(attachedPos);
        return attachedState.isFaceSturdy(world, attachedPos, direction);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(world, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0;
    }
}
