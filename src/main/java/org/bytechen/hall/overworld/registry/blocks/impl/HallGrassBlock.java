package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantBlock;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantSoil;

/**
 * 王庭草 —— 只生长在泥土类方块（含模组的王庭草方块 / 王庭泥土）上的十字模型植物。
 * <p>
 * 支撑方块一旦被替换或是空气，立刻无法存活 → 掉落自身，不会再浮空。
 */
public class HallGrassBlock extends HallPlantBlock {

    public HallGrassBlock() {
        super(HallPlantBlock.createPlantProps());
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return HallPlantSoil.isHallDirt(state);
    }
}
