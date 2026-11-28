package org.bytechen.rottenruins.client.entity.model;

import org.bytechen.rottenruins.api.render.IAutoRenderableEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.model.GeoModel;

public class GeoEntityModel<T extends Entity & IAutoRenderableEntity> extends GeoModel<T> {

    @Override
    public ResourceLocation getModelResource(T entity) { return entity.model(); }

    @Override
    public ResourceLocation getTextureResource(T entity) { return entity.texture(); }

    @Override
    public ResourceLocation getAnimationResource(T entity) { return entity.animation(); }

    @Override
    public RenderType getRenderType(T entity, ResourceLocation texture) {
        return entity.translucentTexture() ? RenderType.entityTranslucent(texture)
                : super.getRenderType(entity, texture);
    }
}
