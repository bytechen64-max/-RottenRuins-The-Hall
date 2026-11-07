package org.bytechen.rottenruins.client.entity.render;

import org.bytechen.rottenruins.client.entity.IAutoRenderableEntity;
import org.bytechen.rottenruins.client.entity.model.GeoEntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GeoEntityRender<T extends Entity & IAutoRenderableEntity> extends GeoEntityRenderer<T> {
    public GeoEntityRender(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoEntityModel<>());
    }
}
