package org.bytechen.hall.overworld.registry.blocks.base;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.api.block.ISpreadBlock;
import org.bytechen.infcore.core.blockspread.BlockSpreadManager;
import org.bytechen.infcore.core.blockspread.SpreadHelper;

/**
 * 可扩散方块的抽象基类。
 * <p>
 * 实现 {@link ISpreadBlock} 接口以声明本模组的扩散类型 {@code hall:spread}。
 * <p>
 * 方块在 {@link #randomTick} 中自动向邻近方块扩散，具体转换规则
 * 由 infcore 的 {@link BlockSpreadManager} 根据 datagen 生成的 JSON 数据执行。
 * <p>
 * 子类只需重写 {@link #canSurvive} / {@link #getDegradedState} 等防护方法。
 */
public abstract class AbstractSpreadBlock extends Block implements ISpreadBlock {

    /** 每次 randomTick 尝试扩散的次数 */
    private static final int SPREAD_ATTEMPTS = 3;

    /** 每次尝试扩散的基础概率 */
    private static final float SPREAD_CHANCE = 0.5F;

    public AbstractSpreadBlock(Properties properties) {
        super(properties);
    }

    // ==================== ISpreadBlock ====================

    @Override
    public ResourceLocation getSpreadType() {
        return new ResourceLocation(HallMod.MODID, "spread");
    }

    // ==================== Tick ====================

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 生存检测：不满足条件则退化
        if (!canSurvive(level, pos, state)) {
            BlockState degraded = getDegradedState(state, level, pos);
            if (degraded != null && !degraded.equals(state)) {
                level.setBlockAndUpdate(pos, degraded);
            }
            return;
        }

        // 向邻近方块扩散（使用 infcore 的 SpreadHelper 快捷方法）
        SpreadHelper.trySpreadNearby(state, level, pos, random, this, SPREAD_ATTEMPTS, SPREAD_CHANCE);

        // 子类自定义额外逻辑
        extraTickLogic(state, level, pos, random);
    }

    // ==================== 子类可重写 ====================

    /**
     * 额外的 tick 逻辑，子类可重写。
     */
    protected void extraTickLogic(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /**
     * 检查该方块是否能在当前位置存活。
     * 默认返回 true。子类可重写以实现退化逻辑。
     */
    protected boolean canSurvive(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    /**
     * 获取退化后的方块状态。
     * 当 {@link #canSurvive} 返回 false 时调用。
     * 默认返回 null（不退化）。
     */
    protected BlockState getDegradedState(BlockState state, ServerLevel level, BlockPos pos) {
        return null;
    }

    /**
     * 检查是否可以扩散到目标方块。
     * 默认委托给 {@link SpreadHelper#canSpreadTo}。
     */
    protected boolean canSpreadTo(LevelReader level, BlockPos pos, BlockState state) {
        return SpreadHelper.canSpreadTo(level, pos, state, this);
    }



    /**
     * 检查是否可以扩散到目标方块。
     * 默认委托给 {@link SpreadHelper#canSpreadTo}。
     */
    public static int addNumber(int number1, int number2)
    {
        return number2+number1;
    }
}
