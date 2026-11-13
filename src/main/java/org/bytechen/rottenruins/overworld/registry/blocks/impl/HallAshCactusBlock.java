package org.bytechen.rottenruins.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.overworld.registry.blocks.base.HallPlantSoil;

/** 王庭烬痕仙人掌：对齐原版仙人掌的全部方块特性，只把土壤换成模组的沙子系。 */
public class HallAshCactusBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;

    /** 最大堆叠高度（与原版一致：3 格）。 */
    public static final int MAX_HEIGHT = 3;

    /** 生长到该 AGE 时向上长出一节。 */
    private static final int GROWTH_AGE = 15;

    /** 选中 / 描边形状：高 16（原版仙人掌上方那格是完整的）。 */
    private static final VoxelShape OUTLINE_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    /** 碰撞形状：高 15，玩家可以站在仙人掌顶上（原版行为）。 */
    private static final VoxelShape COLLISION_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public HallAshCactusBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.CACTUS)
                // 不用 noOcclusion()：它会顺带打开 dynamicShape，把模型按状态逐个缓存；
                // 靠 Properties.copy 带来的遮挡剔除，表现最接近原版仙人掌。
                .noCollission()
                .instabreak()
                .randomTicks());
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    /** 放置白名单：只能贴沙子类方块或叠在本方块上；Block 无此钩子，故不加 @Override。 */
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return HallPlantSoil.isCactusSoil(state, this);
    }

    /** 水平邻块是否让这株仙人掌"贴不上去"。
     *  必须显式列出仙人掌：其 isSolid()/canOcclude() 恒为 false，否则同种能并排贴合。 */
    private boolean isBlockedByNeighbor(BlockGetter level, BlockPos neighborPos, BlockState neighbor) {
        return neighbor.is(this)
                || neighbor.is(Blocks.CACTUS)
                || neighbor.isSolid()
                || level.getFluidState(neighborPos).is(FluidTags.LAVA);
    }

    /** 四向水平检测：任何一面被挡住都不能生存 / 不能放置。 */
    private boolean hasBlockedNeighbor(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            if (this.isBlockedByNeighbor(level, neighborPos, level.getBlockState(neighborPos))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        if (this.hasBlockedNeighbor(level, pos)) {
            return null;
        }
        if (!this.mayPlaceOn(level.getBlockState(pos.below()), level, pos.below())) {
            return null;
        }
        return this.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (this.hasBlockedNeighbor(level, pos)) {
            return false;
        }

        BlockState below = level.getBlockState(pos.below());
        if (!HallPlantSoil.isCactusSoil(below, this)) {
            return false;
        }

        return !level.getBlockState(pos.above()).liquid();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            // 支撑消失 → 下一 tick 掉落自身（而不是瞬间消失，保留原版的手感）
            level.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        BlockPos above = pos.above();
        if (!level.isEmptyBlock(above)) {
            return;
        }

        int height = 1;
        while (level.getBlockState(pos.below(height)).is(this)) {
            height++;
        }

        if (height < MAX_HEIGHT) {
            int age = state.getValue(AGE);
            if (age == GROWTH_AGE) {
                level.setBlockAndUpdate(above, this.defaultBlockState());
                BlockState reset = state.setValue(AGE, 0);
                level.setBlock(pos, reset, Block.UPDATE_CLIENTS);
                level.neighborChanged(reset, above, this, pos, false);
            } else {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.hurt(level.damageSources().cactus(), 1.0F);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
