package org.bytechen.hall.overworld.registry.blocks.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantBlock;
import org.bytechen.hall.overworld.registry.blocks.base.HallPlantSoil;

/**
 * 王庭烬痕枯灌木 —— 十字模型枯灌木。
 * <p>
 * 土壤对齐原版枯灌木：沙子系、泥土系、陶瓦。
 * 不能浮空，也不能放在石头、木板之类无关方块上。
 */
public class HallDeadBushBlock extends HallPlantBlock {

    public HallDeadBushBlock() {
        super(HallPlantBlock.createPlantProps());
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return HallPlantSoil.isDeadBushSoil(state);
    }
}
