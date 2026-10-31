package org.bytechen.rottenruins.overworld.registry.blockentity;

import org.bytechen.rottenruins.api.mask.BlockMaskLayerProvider;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.overworld.registry.RegisterBlockEntities;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.DeathCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 死亡核心的方块实体。
 *
 * <p>它本身没有数据/动画状态（着色器用世界时间自己动），存在的唯一理由是：
 * <b>只有带 BlockEntity 的方块才会被 {@code BlockEntityRenderer} 每帧回调</b>，
 * 而方块版的 mask 层正是挂在 BER 上的（静态方块的几何在区块 mesh 里烘死，
 * 帧循环里没有逐方块的绘制调用点）。</p>
 *
 * <p>层配置由 {@link BlockMaskLayerProvider} 自描述，这里直接转发
 * {@link DeathCoreBlock#MASK_LAYERS}（共享常量，热路径零分配）。</p>
 */
public class DeathCoreBlockEntity extends BlockEntity implements BlockMaskLayerProvider {

    public DeathCoreBlockEntity(BlockPos pos, BlockState state) {
        super(RegisterBlockEntities.DEATH_CORE.get(), pos, state);
    }

    @Override
    public List<MaskLayerSpec> maskLayers(BlockState state) {
        return DeathCoreBlock.MASK_LAYERS;
    }
}
