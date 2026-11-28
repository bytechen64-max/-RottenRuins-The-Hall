package org.bytechen.rottenruins.api.mask;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 方块自描述的 mask 效果层接口 —— 对应物品侧的 {@link MaskLayerProvider}。
 *
 * <h3>为什么方块需要单独一条接口</h3>
 * <p>物品侧的层是「每帧每物品」在 {@code ItemRenderer} 的注入点里解析的；方块没有
 * 这个逐方块调用点（静态方块在区块 mesh 里烘死），所以方块版的层挂在
 * <b>BlockEntityRenderer</b> 上：BER 每帧渲染某个方块实体时，向它要一次层列表。
 * 因此这个接口的方法签名用 {@link BlockState} 而不是 {@code ItemStack}。</p>
 *
 * <h3>实现方必须是 BlockEntity</h3>
 * <p>本接口只是「谁能提供层」，真正驱动渲染的是 BER。没有 BlockEntity 的方块
 * 挂不上 BER，也就没有逐帧调用点 —— 那类方块如果也想发光，应把效果烘进
 * 模型/贴图，而不是走本系统（见 docs 里关于"层 vs 烘焙"的说明）。</p>
 *
 * <p>实现通常直接返回一个 {@code static final List<MaskLayerSpec>} 常量，
 * 因为这个方法<b>每帧每个可见方块都会被调用</b>，不该在热路径里分配。</p>
 */
public interface BlockMaskLayerProvider {

    /** 该方块当前状态要挂的 mask 层；没有则返回空列表（共享单例，不分配）。 */
    List<MaskLayerSpec> maskLayers(BlockState state);
}
