package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.HallMod;
import org.bytechen.infcore.api.block.ISpreadBlock;
import org.bytechen.infcore.core.blockspread.SpreadHelper;

/**
 * 可扩散的柱状方块（RotatedPillarBlock + ISpreadBlock）。
 * <p>
 * 拥有 AXIS 旋转属性，同时具备扩散能力。
 * 用于需要自行扩散的原木等方块。
 */
public class SpreadPillarBlock extends RotatedPillarBlock implements ISpreadBlock {

    /** 每次 randomTick 尝试扩散的次数 */
    private static final int SPREAD_ATTEMPTS = 3;

    /** 每次尝试扩散的基础概率 */
    private static final float SPREAD_CHANCE = 0.5F;

    public SpreadPillarBlock(Properties properties) {
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
        if (!canSurvive(level, pos, state)) {
            BlockState degraded = getDegradedState(state, level, pos);
            if (degraded != null && !degraded.equals(state)) {
                level.setBlockAndUpdate(pos, degraded);
            }
            return;
        }

        SpreadHelper.trySpreadNearby(state, level, pos, random, this,
                SPREAD_ATTEMPTS, SPREAD_CHANCE);
    }

    // ==================== 子类可重写 ====================

    protected boolean canSurvive(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    protected BlockState getDegradedState(BlockState state, ServerLevel level, BlockPos pos) {
        return null;
    }
}
