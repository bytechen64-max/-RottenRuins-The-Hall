package org.bytechen.rottenruins.client.entity;

import org.bytechen.rottenruins.client.entity.model.GeoBaseModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animation.AnimationState;

public interface IGeoModelBehavior {
    default void onModelCustomAnimations(GeoBaseModel<?> model, LivingEntity entity, long uniqueId, AnimationState<?> state) {}
    default void preRender(GeoBaseModel<?> model, LivingEntity entity, PoseStack poseStack, float partialTick) {}
}
