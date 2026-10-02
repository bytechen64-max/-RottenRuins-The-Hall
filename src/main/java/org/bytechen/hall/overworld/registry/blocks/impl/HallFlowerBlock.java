package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantBlock;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantSoil;

/**
 * 王庭花 —— 只生长在泥土类方块（含模组的王庭草方块 / 王庭泥土）上的十字模型植物。
 */
public class HallFlowerBlock extends HallPlantBlock {

    public HallFlowerBlock() {
        super(HallPlantBlock.createPlantProps());
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return HallPlantSoil.isHallDirt(state);
    }
}
