package org.bytechen.hall.overworld.registry.blocks.impl;

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
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantSoil;

/**
 * 王庭烬痕仙人掌 —— 对齐原版仙人掌的全部方块特性，只把土壤换成模组的沙子系。
 * <p>
 * 修正的旧问题：
 * <ul>
 *   <li><b>单个浮空</b>：{@code canSurvive} 只认沙子 / 仙人掌；不满足时
 *       延迟 1 tick 破坏自身，沙子被挖掉后不会再留一株悬空的仙人掌。</li>
 *   <li><b>能种在任何方块上</b>：{@link #mayPlaceOn} 收口为「沙子标签或另一株仙人掌」，
 *       石头 / 木板 / 草方块上放置会被拒绝。</li>
 *   <li><b>两株并排贴合</b>：侧面邻居判定里显式排除同种仙人掌与原版仙人掌
 *       （见 {@link #isBlockedByNeighbor}），并提前到
 *       {@link #getStateForPlacement} 拒绝放置。</li>
 *   <li><b>侧面挨着不透明方块</b>：四面有遮挡方块时自动断裂掉落。</li>
 *   <li><b>生长方向</b>：保留原版 AGE 机制（最多 3 格高）。</li>
 * </ul>
 * 模型黑边问题在数据生成侧解决（见 {@code BlockStateData#cactusBlockWithItem}）。
 */
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
                // 不用 noOcclusion()：它会顺带打开 dynamicShape，让模型被逐状态缓存；
                // 原版仙人掌本来就是「中心柱 + 四片内缩侧片」的结构，靠 Properties.copy
                // 带来的遮挡剔除表现最接近原版。
                .noCollission()
                .instabreak()
                .randomTicks());
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    // ==================== 状态定义 ====================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    // ==================== 生存 / 断裂 ====================

    /**
     * 放置白名单：只能贴着沙子类方块，或者叠在另一株王庭烬痕仙人掌上。
     * <p>
     * {@code Block} 本身没有 {@code mayPlaceOn}（那是 {@link net.minecraft.world.level.block.BushBlock}
     * 的钩子），所以这里不写 {@code @Override}。
     */
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return HallPlantSoil.isCactusSoil(state, this);
    }

    /**
     * 水平邻块是否让这株仙人掌"贴不上去"。
     * <p>
     * 这里必须显式列出仙人掌方块：{@code BlockState#isSolid()} 对仙人掌是 {@code false}
     * （仙人掌属性带 {@code noOcclusion()}，{@code isSolid} 与 {@code canOcclude} 都是 false），
     * 所以仅靠"被遮挡"判定的话，同种仙人掌之间会互相"看不见"，两株就能并排贴在一起。
     * <p>
     * 注意别写成 {@code neighbor.canOcclude()} —— 它对仙人掌同样恒为 false，等于没写。
     */
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

        // 侧面已经有仙人掌 / 不透明方块时直接拒绝放置（连手臂挥动都不该发生）
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
        // 1) 侧面不允许贴任何仙人掌（同种或原版），也不允许贴不透明方块 / 岩浆
        if (this.hasBlockedNeighbor(level, pos)) {
            return false;
        }

        // 2) 支撑必须是沙子类方块（含模组王庭烬痕沙子），或者另一株同种仙人掌
        BlockState below = level.getBlockState(pos.below());
        if (!HallPlantSoil.isCactusSoil(below, this)) {
            return false;
        }

        // 3) 上方需要是空气 / 可替换方块，否则不能再叠
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

    // ==================== 生长 ====================

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

    // ==================== 形状 / 受伤 ====================

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
