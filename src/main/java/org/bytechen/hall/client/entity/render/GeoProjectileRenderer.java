package org.bytechen.hall.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.bytechen.hall.client.entity.IAutoRenderableProjectile;
import org.bytechen.hall.client.entity.IBillboardRenderable;
import org.bytechen.hall.client.entity.model.GeoProjectileModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GeoProjectileRenderer<T extends Entity & IAutoRenderableProjectile> extends GeoEntityRenderer<T> {
    public GeoProjectileRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoProjectileModel<>());
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity instanceof IBillboardRenderable) {
            poseStack.pushPose();
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        } else {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
