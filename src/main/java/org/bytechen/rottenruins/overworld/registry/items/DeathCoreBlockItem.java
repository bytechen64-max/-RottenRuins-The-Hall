package org.bytechen.rottenruins.overworld.registry.items;

import org.bytechen.rottenruins.api.mask.BlockMaskLayerItem;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.overworld.registry.blocks.impl.DeathCoreBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 死亡核心的方块物品。
 *
 * <p>普通 {@link BlockItem} 只负责放置；这里额外实现 {@link BlockMaskLayerItem}，
 * 让它在<b>手持 / 物品栏 / 掉落物</b>形态下也能显示 {@code death_core} 表面层
 * （mask 窗口 + ShaderToy "Ghosts" 体积光）。渲染钩子在 {@code MixinItemRendererCosmic}，
 * 用的是物品模型自己的 quad —— 对 block item 来说就是那个 cube，
 * 所以和世界里 BER 画出来的形状、UV 映射完全一致。</p>
 *
 * <p>层列表直接转发 {@link DeathCoreBlock#MASK_LAYERS}，热路径零分配。</p>
 */
public class DeathCoreBlockItem extends BlockItem implements BlockMaskLayerItem {

    public DeathCoreBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public List<MaskLayerSpec> maskLayers(ItemStack stack) {
        return DeathCoreBlock.MASK_LAYERS;
    }
}
