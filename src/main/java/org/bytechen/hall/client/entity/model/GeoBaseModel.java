package org.bytechen.hall.client.entity.model;

import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.client.entity.IGeoModelBehavior;
import org.bytechen.hall.overworld.registry.ClientRenderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class GeoBaseModel<T extends LivingEntity & IAutoRenderableEntity> extends GeoModel<T> {

    @Override
    public ResourceLocation getModelResource(T entity) { return entity.model(); }

    @Override
    public ResourceLocation getTextureResource(T entity) { return entity.texture(); }

    @Override
    public ResourceLocation getAnimationResource(T entity) { return entity.animation(); }

    @Override
    public void setCustomAnimations(T entity, long uniqueId, AnimationState<T> animationState) {
        super.setCustomAnimations(entity, uniqueId, animationState);
        IGeoModelBehavior behavior = ClientRenderRegistry.getModelBehavior(entity.getType());
        if (behavior != null) {
            behavior.onModelCustomAnimations(this, entity, uniqueId, animationState);
        }
    }
}
