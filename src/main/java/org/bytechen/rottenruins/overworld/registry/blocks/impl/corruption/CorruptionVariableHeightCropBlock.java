package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

/**
 * 腐染经络 / 可生长高茎（从 corruption 工程 {@code CorruptionVariableHeightCropBlock} 移植）。
 *
 * <p>{@code age} 0~25 控制生长进度，{@code section=BOTTOM/MIDDLE/TOP} 控制用哪段模型
 * （datagen 生成三张 cross 模型并按 section 映射 blockstate）。只认腐染泥土作为底面。</p>
 */
public class CorruptionVariableHeightCropBlock extends Block {
    /** AGE 用来控制生长进度 (0-24)，类似海带，25 表示已经长成/作为茎干。 */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    /** SECTION 属性，用于模型判断：BOTTOM, MIDDLE, TOP。 */
    public static final EnumProperty<StemSection> SECTION = EnumProperty.create("section", StemSection.class);

    protected static final VoxelShape THICK_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    protected static final VoxelShape THIN_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    public CorruptionVariableHeightCropBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(SECTION, StemSection.TOP));
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        // 只有最顶端（没有上方邻居）且 AGE 还没到头的才 Tick
        return state.getValue(AGE) < 25;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        BlockPos abovePos = pos.above();
        if (level.isEmptyBlock(abovePos)) {
            int currentAge = state.getValue(AGE);

            // 计算随机最大高度 (11-15 格)；用坐标哈希保证同一根茎限制固定
            int maxHeight = 11 + (pos.getX() ^ pos.getZ()) % 5;

            int currentHeight = 1;
            while (level.getBlockState(pos.below(currentHeight)).is(this)) {
                currentHeight++;
            }

            if (currentHeight < maxHeight && random.nextDouble() < 0.20D) {
                // 1. 当前位置变为茎干 (AGE = 25)，并自动更新形态
                level.setBlock(pos, this.updateShapeLogic(state.setValue(AGE, 25), level, pos), 4);
                // 2. 上方生成新的顶端
                level.setBlockAndUpdate(abovePos, this.defaultBlockState().setValue(AGE, currentAge + 1));
            }
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return updateShapeLogic(state, level, pos);
    }

    private BlockState updateShapeLogic(BlockState state, LevelReader level, BlockPos pos) {
        boolean hasUp = level.getBlockState(pos.above()).is(this);
        boolean hasDown = level.getBlockState(pos.below()).is(this);

        if (hasUp && hasDown) {
            return state.setValue(SECTION, StemSection.MIDDLE);
        } else if (hasUp) {
            return state.setValue(SECTION, StemSection.BOTTOM);
        } else {
            // 上方没方块了，自己变回顶端并恢复生长能力
            return state.setValue(SECTION, StemSection.TOP).setValue(AGE, Math.min(state.getValue(AGE), 24));
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) || below.is(CorruptionBlocks.SPREAD_DIRT.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 底部稍微粗一点，中段和顶端细一点
        return state.getValue(SECTION) == StemSection.BOTTOM ? THICK_SHAPE : THIN_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SECTION);
    }

    public enum StemSection implements StringRepresentable {
        BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

        private final String name;

        StemSection(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
