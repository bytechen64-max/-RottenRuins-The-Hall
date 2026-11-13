package org.bytechen.rottenruins.api.mask;
import org.bytechen.rottenruins.HallMod;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 物品自描述的 mask 层来源（无需注册）；maskLayers 在渲染热路径每帧调用，勿分配大对象。 */
public interface MaskLayerProvider {

    List<MaskLayerSpec> maskLayers(ItemStack stack);
}
