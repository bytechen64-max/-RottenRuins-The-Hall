package org.bytechen.rottenruins.client.entity.model;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.bytechen.rottenruins.utils.ModUtils;
import software.bernie.geckolib.model.GeoModel;

/**
 * 奥秘仪台的 GeckoLib 模型：geo 里没有动画，只需模型与贴图。
 */
public final class PotentialCraftingTableModel extends GeoModel<PotentialCraftingTableBlockEntity> {

    @Override
    public ResourceLocation getModelResource(PotentialCraftingTableBlockEntity animatable) {
        return ModUtils.modLoc("geo/potential_crafting_table.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PotentialCraftingTableBlockEntity animatable) {
        return ModUtils.modLoc("textures/block/potential_crafting_table.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PotentialCraftingTableBlockEntity animatable) {
        return null;
    }
}
