package org.bytechen.hall.client.entity.model;

import org.bytechen.hall.api.IAutoRenderableItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.model.GeoModel;

public class GeoItemModel<T extends Item & IAutoRenderableItem> extends GeoModel<T> {

    private static final ResourceLocation EMPTY_ANIMATION =
            ResourceLocation.withDefaultNamespace("animations/empty.animation.json");

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return animatable.model();
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return animatable.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        ResourceLocation anim = animatable.animation();
        return anim != null ? anim : EMPTY_ANIMATION;
    }
}
