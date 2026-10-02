package org.bytechen.hall.overworld.registry.blocks.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;

/**
 * 王庭植物基类 —— 供王庭草 / 王庭花 / 王庭烬痕枯灌木等“十字模型”植物使用。
 * <p>
 * 原版 {@link BushBlock} 负责「上方是空气 + 长在泥土类方块上」的通用判定，
 * 本类把支撑判定收口成一条链：
 * <ol>
 *   <li>{@link #isSupportedBy} 先确认支撑方块已经注册（数据生成 / 注册期不反查崩溃）；</li>
 *   <li>再交给子类实现的 {@link #mayPlaceOn} 白名单（沙子 / 泥土 / 王庭化方块……）。</li>
 * </ol>
 * 只要支撑方块不再是白名单里的方块，{@code canSurvive} 立即为 false，
 * 于是既不会凭空浮空，也不会被放置在任何方块上。
 */
public class HallPlantBlock extends BushBlock implements IPlantable {

    public HallPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * 植物通用属性：无碰撞、瞬间破坏、草丛音效、不接受遮挡（避免相邻方块压暗出黑边）。
     */
    public static BlockBehaviour.Properties createPlantProps() {
        return BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    /**
     * 子类实现：该植物能否生长在给定方块状态上。
     * 默认拒绝一切支撑（纯装饰基类，请勿直接注册），
     * 具体植物请覆盖为 {@code state.is(BlockTags.SAND)} 之类的白名单判断。
     * <p>
     * 可见性与签名必须跟 {@link BushBlock} 保持一致（public），
     * 否则会被编译器判定为「新方法」而不是覆盖。
     */
    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    /**
     * 带注册校验的支撑判定。
     * 支撑方块尚未注册（注册名返回 null）时直接判为不可种植，
     * 避免数据生成期反查 {@code BuiltInRegistries.BLOCK} 抛异常。
     */
    protected final boolean isSupportedBy(BlockState supportState, BlockGetter level, BlockPos pos) {
        if (supportState.isAir()) {
            return false;
        }
        if (supportState.getBlock().builtInRegistryHolder().key().location() == null) {
            return false;
        }
        return this.mayPlaceOn(supportState, level, pos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return this.isSupportedBy(level.getBlockState(below), level, below);
    }

    // ==================== Forge 种植类型 ====================

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos) {
        return PlantType.PLAINS;
    }

    @Override
    public BlockState getPlant(BlockGetter level, BlockPos pos) {
        return this.defaultBlockState();
    }
}
