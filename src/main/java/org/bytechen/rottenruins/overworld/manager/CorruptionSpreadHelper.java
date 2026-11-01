package org.bytechen.rottenruins.overworld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

/**
 * 腐化方块扩散辅助（一阶召唤柱 / 腐化群系共用）。
 */
public final class CorruptionSpreadHelper {

    /** 腐化扩散类型（与 {@code CorruptionBlockSpreadDataProvider} 对应） */
    public static final ResourceLocation SPREAD_TYPE =
            new ResourceLocation(HallMod.MODID, "corruption");

    private CorruptionSpreadHelper() {}

    /**
     * 在中心附近随机扩散若干方块。
     *
     * @param radius   水平半径
     * @param attempts 尝试次数
     * @return 实际感染成功的方块数
     */
    public static int spreadArea(ServerLevel level, BlockPos center, int radius, int attempts) {
        int spread = 0;
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = center.offset(
                    level.random.nextInt(radius * 2 + 1) - radius,
                    level.random.nextInt(4) - 2,
                    level.random.nextInt(radius * 2 + 1) - radius);
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (!org.bytechen.infcore.core.blockspread.BlockSpreadManager.hasSpreadRule(state, SPREAD_TYPE)) {
                continue;
            }
            if (org.bytechen.infcore.core.blockspread.BlockSpreadManager.applySpread(level, pos, SPREAD_TYPE)) {
                spread++;
            }
        }
        return spread;
    }

    /** 是否腐化地面（用于自然生成的落点判断）。 */
    public static boolean isCorruptionGround(BlockState state) {
        return state.is(CorruptionBlocks.SPREAD_DIRT.get())
                || state.is(CorruptionBlocks.SPREAD_STONE.get())
                || state.is(CorruptionBlocks.INFECTION_COBBLESTONE_BLOCK.get())
                || state.is(CorruptionBlocks.SPREAD_INFECTION_LEAVES.get())
                || state.is(CorruptionBlocks.SPREAD_INFECTION_WOOD.get())
                || state.is(CorruptionBlocks.CORRUPTION_GRASS.get())
                || state.is(CorruptionBlocks.CORRUPTION_TRACHEA.get())
                || state.is(CorruptionBlocks.CONNECT_BLOCK.get())
                || state.is(CorruptionBlocks.DECIDUAL_TISSUE_BLOCK.get())
                || state.is(CorruptionBlocks.FLESHBED.get())
                || state.is(CorruptionBlocks.VARIABLE_HEIGHT_CROP.get())
                || state.is(CorruptionBlocks.SPIRACLE_BLOCK.get());
    }
}
