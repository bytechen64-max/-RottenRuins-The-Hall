package org.bytechen.rottenruins.overworld.registry.blocks.impl.corruption;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bytechen.infcore.api.block.ISpreadBlock;
import org.bytechen.infcore.core.blockspread.SpreadHelper;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionBlocks;

/**
 * 腐化扩散方块 —— 与王庭 {@code AbstractSpreadBlock} 相同的自我扩散逻辑。
 *
 * <p>在 {@link #randomTick} 中通过 infcore 的 {@link SpreadHelper#trySpreadNearby}
 * 把相邻的可感染方块转换成腐化方块；扩散规则来自
 * {@code data/rotten_ruins/infcore_blockspread/corruption.json}。</p>
 *
 * <p>每次扩散都会触发 {@code BlockSpreadEvent.Post}，从而带动腐化群系一起扩张
 * （见 {@code CorruptionBiomeSpreadHandler}）。</p>
 */
public class CorruptionSpreadBlock extends Block implements ISpreadBlock {

    /** 每次 randomTick 尝试扩散的次数 */
    private static final int SPREAD_ATTEMPTS = 3;
    /** 每次尝试的基础成功概率 */
    private static final float SPREAD_CHANCE = 0.5F;

    public CorruptionSpreadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation getSpreadType() {
        return new ResourceLocation(HallMod.MODID, "corruption");
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SpreadHelper.trySpreadNearby(state, level, pos, random, this, SPREAD_ATTEMPTS, SPREAD_CHANCE);

        // 腐土顶面偶尔长出腐化矮草（让扩散区域更像群系）
        if (state.is(CorruptionBlocks.SPREAD_DIRT.get())
                && level.getBlockState(pos.above()).isAir()
                && random.nextFloat() < 0.04F) {
            level.setBlockAndUpdate(pos.above(), CorruptionBlocks.CORRUPTION_GRASS.get().defaultBlockState());
        }
    }
}
