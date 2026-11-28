package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.overworld.energy.SourceWireNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** 独立于红石信号的源导线；复用灰度贴图和连接方向，不接入原版红石电路。 */
public final class SourceWireBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH_REDSTONE,
            Direction.EAST, BlockStateProperties.EAST_REDSTONE,
            Direction.SOUTH, BlockStateProperties.SOUTH_REDSTONE,
            Direction.WEST, BlockStateProperties.WEST_REDSTONE);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    public SourceWireBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(POWER, 0);
        for (var property : SIDES.values()) state = state.setValue(property, RedstoneSide.NONE);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
        SIDES.values().forEach(builder::add);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || level.getBlockState(below).is(Blocks.HOPPER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return canSurvive(defaultBlockState(), context.getLevel(), context.getClickedPos())
                ? connections(context.getLevel(), context.getClickedPos(), defaultBlockState()) : null;
    }

    private static boolean loaded(BlockGetter level, BlockPos pos) {
        return !(level instanceof Level world) || world.hasChunkAt(pos);
    }

    /** 同层或一格台阶的导线邻居；所有世界读取都先检查区块已加载。 */
    public @Nullable BlockPos wireNeighbor(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos side = pos.relative(direction);
        if (!loaded(level, side)) return null;
        BlockState adjacent = level.getBlockState(side);
        if (adjacent.is(this)) return side;
        if (adjacent.isFaceSturdy(level, side, Direction.UP)
                && !level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())
                && level.getBlockState(side.above()).is(this)) return side.above();
        if (!adjacent.isRedstoneConductor(level, side) && level.getBlockState(side.below()).is(this)) return side.below();
        return null;
    }

    public BlockState connections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            BlockPos wire = wireNeighbor(level, pos, direction);
            RedstoneSide connection = wire != null
                    ? (wire.getY() > pos.getY() ? RedstoneSide.UP : RedstoneSide.SIDE)
                    : loaded(level, side) && (level.getBlockState(side).getBlock() instanceof GroundingStakeBlock
                    || SourceMachineBlock.hasPort(level.getBlockState(side),direction.getOpposite()))
                    ? RedstoneSide.SIDE : RedstoneSide.NONE;
            state = state.setValue(SIDES.get(direction), connection);
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        level.scheduleTick(pos, this, 1);
        return connections(level, pos, state);
    }

    private void scheduleNearby(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        ServerLevel server=(ServerLevel)level;
        SourceWireNetwork.invalidate(server);
        // 台阶两端可能相距一个水平格加一个垂直格，需要同时通知。
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos other = pos.relative(direction).offset(0, dy, 0);
                if (level.hasChunkAt(other)) {
                    BlockState adjacent=level.getBlockState(other);
                    if (adjacent.is(this)) level.scheduleTick(other,this,1);
                    if (adjacent.is(this) || adjacent.getBlock() instanceof SourceMachineBlock) SourceWireNetwork.refresh(server,other,this);
                }
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
            scheduleNearby(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!newState.is(this)) scheduleNearby(level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        if (!level.isClientSide) {
            if (!canSurvive(state, level, pos)) {
                dropResources(state, level, pos);
                level.removeBlock(pos, false);
            } else {
                level.scheduleTick(pos,this,1);
                if (neighbor instanceof GroundingStakeBlock || neighbor instanceof SourceMachineBlock) {
                    SourceWireNetwork.invalidate((ServerLevel)level);
                    SourceWireNetwork.refresh((ServerLevel)level,pos,this);
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SourceWireNetwork.refresh(level, pos, this);
        // 周期对齐以便同一网络每轮只计算一次，也让跨区块加载与密度修改自动收敛。
        level.scheduleTick(pos, this, (int) (20 - level.getGameTime() % 20));
    }
}
