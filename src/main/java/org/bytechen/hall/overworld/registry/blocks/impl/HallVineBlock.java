package org.bytechen.hall.overworld.registry.blocks.impl;

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
import org.bytechen.infcore.api.block.ISpreadBlock;

/**
 * 王庭藤蔓 —— 可在王庭化方块上双向生长的藤蔓植物。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>只能种植在实现了 {@link ISpreadBlock} 且 getSpreadType() 命名空间为 "hall" 的方块上</li>
 *   <li>支持向上生长（种植在方块顶面，最高 8 格）</li>
 *   <li>支持向下生长（悬挂在方块底面，最长 15 格）</li>
 *   <li>使用 SECTION + GROWTH_DIR 组合控制不同段的模型</li>
 * </ul>
 */
public class HallVineBlock extends Block {

    /** 生长进度 (0-24)，25 表示已成熟茎段，不再 randomTick */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;

    /** 藤蔓段：BOTTOM（附着端）、MIDDLE（茎段）、TOP（生长端） */
    public static final EnumProperty<VineSection> SECTION =
            EnumProperty.create("section", VineSection.class);

    /** 生长方向 */
    public static final EnumProperty<GrowthDirection> GROWTH_DIR =
            EnumProperty.create("growth_dir", GrowthDirection.class);

    protected static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    /** 每次 randomTick 的生长概率 */
    private static final double GROWTH_CHANCE = 1.0D;

    /**
     * 向上生长的最大高度（单位为「节」）。
     * <p>
     * 这里限制的是<b>生长上限</b>，与「世界生成时一次放几格」是两回事：
     * 地物（{@code HallVineFeature}）只放置<b>一格</b>作为种子，
     * 之后由 {@link #randomTick} 逐节往上长到这个上限。
     * <p>
     * 曾经为了绕开「多段连接态贴图不对」的问题把此值压成 1，
     * 那是误解了需求 —— 需求是「生成只有一格」，不是「不能生长」。
     * 现在恢复生长，多段状态的刷新由 {@code randomTick} 末尾的
     * {@code refreshNeighbourSection} 负责。
     */
    private static final int MAX_UP_HEIGHT = 4;

    /** 向下生长的最大长度（单位为「节」）。含义同 {@link #MAX_UP_HEIGHT}。 */
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

    // ==================== 放置逻辑 ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();

        // BlockItemPlaceContext.getClickedPos() 返回的是藤蔓将被放置的位置
        // （已经沿点击面偏移），所以支撑方块在点击面的对面方向。
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

        // 放置时初始 SECTION：单根藤蔓没有邻居，UP 生长端是 TOP，DOWN 生长端是 BOTTOM
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
            return "hall".equals(spreadBlock.getSpreadType().getNamespace());
        }
        return false;
    }

    // ==================== 生存检测 ====================

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

    // ==================== Tick ====================

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

        // 计算最大允许高度
        int maxHeight = isUp ? MAX_UP_HEIGHT : MAX_DOWN_HEIGHT;

        // 计算当前位置已有多少节藤蔓
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

        // 生长：
        // 1. 当前位置变为成熟茎段（AGE=25）
        level.setBlock(pos, this.updateShapeLogic(state.setValue(AGE, 25), level, pos), Block.UPDATE_ALL);

        // 2. 前方生成新的生长端
        int newAge = Math.min(state.getValue(AGE) + 1, 24);
        level.setBlockAndUpdate(forwardPos, this.defaultBlockState()
                .setValue(GROWTH_DIR, state.getValue(GROWTH_DIR))
                .setValue(AGE, newAge));

        // 3. 【关键】显式重算相邻两段的形态。
        //
        // 为什么必须手动做：新段是用 setBlockAndUpdate 放置的，它虽然带
        // UPDATE_NEIGHBORS，但只保证「紧邻方块的邻居通知」，并不保证通知会
        // 沿藤蔓继续往回传播。结果是先长出来的那几段永远停在初始状态
        // （向下长的停在 BOTTOM、向上长的停在 TOP），整条藤蔓只有一两段
        // 用对了贴图 —— 这正是「连着都是同一个状态」的原因。
        //
        // 正反两段都刷新，保证无论谁先更新都能得到正确的中间态。
        BlockPos backwardPos = isUp ? pos.below() : pos.above();
        refreshNeighbourSection(level, backwardPos);
        refreshNeighbourSection(level, forwardPos);
    }

    /**
     * 重算某一格的 SECTION（若该格是王庭藤蔓）。
     * <p>
     * 由于邻居变化不会沿链条自动回传，生长后需要显式刷新相邻段，
     * 否则整条藤蔓的贴图状态会滞留在初始值。
     */
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

    // ==================== 形态更新 ====================

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction supportDir = state.getValue(GROWTH_DIR) == GrowthDirection.UP
                ? Direction.DOWN : Direction.UP;

        // 支撑方向方块消失 → 自己也要死
        if (direction == supportDir && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        // 上下邻居变化时重新计算 SECTION
        return this.updateShapeLogic(state, level, pos);
    }

    /**
     * 根据上下邻居和生长方向计算当前段的 SECTION。
     * <p>
     * 分支逻辑（以向上生长为例）：
     * <ul>
     *   <li>上方有藤蔓 + 下方有藤蔓 → MIDDLE</li>
     *   <li>上方有藤蔓 + 下方是支撑方块 → BOTTOM（附着段）</li>
     *   <li>上方无藤蔓 → TOP（生长端），AGE 限制 &lt; 25 以保持 ticking</li>
     * </ul>
     */
    private BlockState updateShapeLogic(BlockState state, LevelReader level, BlockPos pos) {
        boolean isUp = state.getValue(GROWTH_DIR) == GrowthDirection.UP;

        BlockPos forwardPos = isUp ? pos.above() : pos.below();
        BlockPos backwardPos = isUp ? pos.below() : pos.above();

        boolean hasForward = level.getBlockState(forwardPos).is(this);
        boolean hasBackward = level.getBlockState(backwardPos).is(this);

        if (hasForward && hasBackward) {
            return state.setValue(SECTION, VineSection.MIDDLE);
        } else if (hasForward) {
            // 前方有藤蔓，后方无 → 基段/附着段
            VineSection baseSection = isUp ? VineSection.BOTTOM : VineSection.TOP;
            return state.setValue(SECTION, baseSection);
        } else {
            // 前方无藤蔓 → 生长端，AGE 保持 <25 使其继续 ticking
            VineSection tipSection = isUp ? VineSection.TOP : VineSection.BOTTOM;
            return state.setValue(SECTION, tipSection)
                    .setValue(AGE, Math.min(state.getValue(AGE), 24));
        }
    }

    // ==================== 碰撞箱 & 渲染 ====================

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

    // ==================== 燃烧特性 ====================

    /**
     * 王庭藤蔓的燃烧特性 —— 对齐原版 {@code minecraft:vine}（引燃 15 / 可燃 100）。
     * <p>
     * 原版把数值写死在 {@code FireBlock} 的私有表里，模组方块必须自行重写；
     * 详见 {@link HallWoodBlocks}。
     */
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return HallWoodBlocks.VINE_FLAMMABILITY;
    }

    /** 王庭藤蔓的火焰蔓延速度 —— 对齐原版 {@code minecraft:vine} */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return HallWoodBlocks.VINE_ENCOURAGEMENT;
    }

    // ==================== 状态定义 ====================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SECTION, GROWTH_DIR);
    }

    // ==================== 枚举 ====================

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
