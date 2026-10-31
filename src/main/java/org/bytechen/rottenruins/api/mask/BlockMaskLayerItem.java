package org.bytechen.rottenruins.api.mask;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 「方块物品」携带 mask 层的接口 —— 让手持/背包里的方块物品也能显示方块版
 * mask 着色器层（{@code death_core} 等）。
 *
 * <h3>和 {@link MaskLayerProvider} 的分工</h3>
 * <p>{@link MaskLayerProvider} 走的是<b>物品贴图平面</b>那条路（{@code MaskLayerRenderer}
 * 用本体贴图烘一张 16×16 平面）。方块物品的显示模型其实是方块模型（cube），
 * 平面叠加层对不上六个面，所以方块物品走另一条路：
 * {@code MixinItemRendererCosmic} 识别到本接口后，直接取<b>物品模型自身的 quad</b>
 * 交给 {@code BlockMaskOverlayRenderer} 绘制 —— 和 BER 里的方块层同一套代码。</p>
 *
 * <p>实现者通常只需要一行转发（见 {@code DeathCoreBlockItem}）。</p>
 */
public interface BlockMaskLayerItem {

    /** 该物品要挂的 mask 层；没有则返回空列表（共享单例，不分配）。 */
    List<MaskLayerSpec> maskLayers(ItemStack stack);
}
