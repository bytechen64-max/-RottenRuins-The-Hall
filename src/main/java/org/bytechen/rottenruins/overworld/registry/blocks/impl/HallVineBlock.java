package org.bytechen.rottenruins.overworld.registry.blocks.impl;
import org.bytechen.rottenruins.HallMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.api.infection.block.ISpreadBlock;

/**
 * 王庭藤蔓 —— 可在王庭化方块（{@link ISpreadBlock} 且 {@code getSpreadType()} 命名空间为 {@code HallMod.MODID}）上双向生长的藤蔓，
 * 支持向上 / 向下生长，用 SECTION + GROWTH_DIR 组合选择不同段的模型。
 */
public class HallVineBlock extends Block {

    /** 生长进度 (0-24)，25 表示已成熟茎段，不再 randomTick */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;

    /** 藤蔓段：BOTTOM（附着端）、MIDDLE（茎段）、TOP（生长端） */
    public static final EnumProperty<VineSection> SECTION =
            EnumProperty.create("section", VineSection.class);

    public static final EnumProperty<GrowthDirection> GROWTH_DIR =
            EnumProperty.create("growth_dir", GrowthDirection.class);

    protected static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    private static final double GROWTH_CHANCE = 1.0D;

    /**
     * 向上生长的最大高度（节）。限制的是<b>生长上限</b>，与地物（{@code HallVineFeature}）只放一格种子是两回事；
     * 多段状态的刷新由 {@link #randomTick} 末尾的 {@code refreshNeighbourSection} 负责。
     */
    private static final int MAX_UP_HEIGHT = 4;

    /** 向下生长的最大长度（节），含义同 {@link #MAX_UP_HEIGHT}。 */
    private static final int MAX_DOWN_HEIGHT = 10;

    public HallVineBlock() {
        super(Properties.of()
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(GROWTH_DIR, GrowthDirection.UP)
                .setValue(SECTION, VineSection.TOP)
                .setValue(AGE, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();

        // getClickedPos() 已沿点击面偏移到藤蔓自己的位置，所以支撑方块在点击面的对面方向。
        BlockPos supportPos = clickedPos.relative(clickedFace.getOpposite());

        GrowthDirection growthDir;
        if (clickedFace == Direction.UP) {
            growthDir = GrowthDirection.UP;
        } else if (clickedFace == Direction.DOWN) {
            growthDir = GrowthDirection.DOWN;
        } else {
            return null;
        }

        if (!canPlantOn(level.getBlockState(supportPos))) {
            return null;
        }

        // 单根藤蔓没有邻居：UP 生长端是 TOP，DOWN 生长端是 BOTTOM
        VineSection tipSection = (growthDir == GrowthDirection.UP) ? VineSection.TOP : VineSection.BOTTOM;

        return this.defaultBlockState()
                .setValue(GROWTH_DIR, growthDir)
                .setValue(SECTION, tipSection)
                .setValue(AGE, 0);
    }

    private static boolean canPlantOn(BlockState supportState) {
        Block block = supportState.getBlock();
        // 已有的王庭藤蔓也可以作为继续放置的支撑
        if (block instanceof HallVineBlock) {
            return true;
        }
        if (block instanceof ISpreadBlock spreadBlock) {
            return HallMod.MODID.equals(spreadBlock.getSpreadType().getNamespace());
        }
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction supportDir = state.getValue(GROWTH_DIR) == GrowthDirection.UP
                ? Direction.DOWN : Direction.UP;
        BlockPos supportPos = pos.relative(supportDir);
        BlockState supportState = level.getBlockState(supportPos);

        if (supportState.is(this)) {
            return true;
        }
        return canPlantOn(supportState);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 25;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        boolean isUp = state.getValue(GROWTH_DIR) == GrowthDirection.UP;
        BlockPos forwardPos = isUp ? pos.above() : pos.below();

        if (!level.isEmptyBlock(forwardPos)) {
            return;
        }

        int maxHeight = isUp ? MAX_UP_HEIGHT : MAX_DOWN_HEIGHT;

        int currentHeight = 1;
        BlockPos checkPos = isUp ? pos.below() : pos.above();
        while (level.getBlockState(checkPos).is(this)) {
            currentHeight++;
            checkPos = isUp ? checkPos.below() : checkPos.above();
        }

        if (currentHeight >= maxHeight) {
            return;
        }

        if (random.nextDouble() >= GROWTH_CHANCE) {
            return;
        }

        // 生长：当前段变为成熟茎段（AGE=25），前方生成新的生长端（AGE 最多 24）
        level.setBlock(pos, this.updateShapeLogic(state.setValue(AGE, 25), level, pos), Block.UPDATE_ALL);

        int newAge = Math.min(state.getValue(AGE) + 1, 24);
        level.setBlockAndUpdate(forwardPos, this.defaultBlockState()
                .setValue(GROWTH_DIR, state.getValue(GROWTH_DIR))
                .setValue(AGE, newAge));

        // 【关键】显式重算相邻两段的形态：setBlockAndUpdate 的邻居通知只保证紧邻方块，不保证沿藤蔓回传，
        // 否则先长出来的几段会一直停在初始 SECTION（整条藤蔓只有一两段贴图正确）。
        // 正反两段都刷新，保证无论谁先更新都能得到正确的中间态。
        BlockPos backwardPos = isUp ? pos.below() : pos.above();
        refreshNeighbourSection(level, backwardPos);
        refreshNeighbourSection(level, forwardPos);
    }

    /** 重算某一格的 SECTION（若该格是王庭藤蔓）；邻居变化不会沿链条自动回传，故生长后需显式刷新相邻段。 */
    private void refreshNeighbourSection(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(this)) {
            return;
        }
        BlockState updated = this.updateShapeLogic(s, level, pos);
        if (!updated.equals(s)) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction supportDir = state.getValue(GROWTH_DIR) == GrowthDirection.UP
                ? Direction.DOWN : Direction.UP;

        // 支撑方向方块消失 → 自己也要死
        if (direction == supportDir && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return this.updateShapeLogic(state, level, pos);
    }

    /** 由上下邻居与生长方向重算 SECTION：前后都有 → MIDDLE，只有前方 → 附着段（UP 为 BOTTOM / DOWN 为 TOP），
     *  前方无 → 生长端，并把 AGE 钳到 &lt; 25 以保持继续 ticking。 */
    private BlockState updateShapeLogic(BlockState state, LevelReader level, BlockPos pos) {
        boolean isUp = state.getValue(GROWTH_DIR) == GrowthDirection.UP;

        BlockPos forwardPos = isUp ? pos.above() : pos.below();
        BlockPos backwardPos = isUp ? pos.below() : pos.above();

        boolean hasForward = level.getBlockState(forwardPos).is(this);
        boolean hasBackward = level.getBlockState(backwardPos).is(this);

        if (hasForward && hasBackward) {
            return state.setValue(SECTION, VineSection.MIDDLE);
        } else if (hasForward) {
            VineSection baseSection = isUp ? VineSection.BOTTOM : VineSection.TOP;
            return state.setValue(SECTION, baseSection);
        } else {
            VineSection tipSection = isUp ? VineSection.TOP : VineSection.BOTTOM;
            return state.setValue(SECTION, tipSection)
                    .setValue(AGE, Math.min(state.getValue(AGE), 24));
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    /** 燃烧特性 —— 对齐原版 {@code minecraft:vine}（引燃 15 / 可燃 100）；原版数值写死在 {@code FireBlock} 私有表里，模组方块必须自行重写。 */
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return HallWoodBlocks.VINE_FLAMMABILITY;
    }

    /** 火焰蔓延速度 —— 对齐原版 {@code minecraft:vine} */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return HallWoodBlocks.VINE_ENCOURAGEMENT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SECTION, GROWTH_DIR);
    }

    public enum VineSection implements StringRepresentable {
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;
        VineSection(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }

    public enum GrowthDirection implements StringRepresentable {
        UP("up"),
        DOWN("down");

        private final String name;
        GrowthDirection(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }
}
