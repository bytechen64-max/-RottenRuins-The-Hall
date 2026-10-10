package org.bytechen.hall.overworld.registry.blocks.base;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.hall.overworld.registry.RegisterBlock;

/**
 * 王庭植物的土壤白名单。
 * <p>
 * 集中定义「哪种植物能长在哪种方块上」，注册与判定都引用这里，
 * 避免各植物类里散落魔法判断。
 */
public final class HallPlantSoil {

    private HallPlantSoil() {
    }

    /** 沙子类土壤：原版沙子 / 红沙 + 模组的王庭烬痕沙子。 */
    public static boolean isSand(BlockState state) {
        return state.is(BlockTags.SAND);
    }

    /** 泥土类土壤：草方块、泥土、砂土、灰化土、缠根泥土 + 模组的王庭草方块 / 王庭泥土。 */
    public static boolean isHallDirt(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(RegisterBlock.HALL_GRASS_BLOCK.get())
                || state.is(RegisterBlock.HALL_DIRT.get());
    }

    /** 枯灌木类土壤：沙子系 + 泥土系 + 陶瓦（对齐原版枯灌木）。 */
    public static boolean isDeadBushSoil(BlockState state) {
        return isSand(state) || isHallDirt(state) || state.is(BlockTags.TERRACOTTA);
    }

    /** 仙人掌土壤：只能贴着沙子，或叠在另一株王庭烬痕仙人掌上。 */
    public static boolean isCactusSoil(BlockState state, Block cactus) {
        return isSand(state) || state.is(cactus);
    }
}
