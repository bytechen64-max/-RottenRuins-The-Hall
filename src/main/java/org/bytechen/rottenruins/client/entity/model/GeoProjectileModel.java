package org.bytechen.rottenruins.client.entity.model;

import org.bytechen.rottenruins.client.entity.IAutoRenderableProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.model.GeoModel;

public class GeoProjectileModel<T extends Entity & IAutoRenderableProjectile> extends GeoModel<T> {

    @Override
    public ResourceLocation getModelResource(T entity) { return entity.model(); }

    @Override
    public ResourceLocation getTextureResource(T entity) { return entity.texture(); }

    @Override
    public ResourceLocation getAnimationResource(T entity) { return entity.animation(); }
}
