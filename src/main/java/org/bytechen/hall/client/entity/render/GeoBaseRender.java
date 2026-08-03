package org.bytechen.hall.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.client.entity.IGeoLayerProvider;
import org.bytechen.hall.client.entity.IGeoModelBehavior;
import org.bytechen.hall.client.entity.model.GeoBaseModel;
import org.bytechen.hall.overworld.registry.ClientRenderRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

public class GeoBaseRender<T extends LivingEntity & IAutoRenderableEntity> extends GeoEntityRenderer<T> {

    public GeoBaseRender(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GeoBaseModel<>());
        this.addRenderLayer(new UniversalDelegateLayer(this));
    }

    @Override
    public void render(T animatable, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        applyPreRenderBehavior(animatable, poseStack, partialTick);
        super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void applyPreRenderBehavior(T animatable, PoseStack poseStack, float partialTick) {
        IGeoModelBehavior behavior = ClientRenderRegistry.getModelBehavior(animatable.getType());
        if (behavior != null) {
            behavior.preRender(null, animatable, poseStack, partialTick);
        }
    }

    @SuppressWarnings("rawtypes")
    private static class UniversalDelegateLayer<T extends LivingEntity & IAutoRenderableEntity> extends GeoRenderLayer<T> {
        public UniversalDelegateLayer(GeoEntityRenderer<T> entityRenderer) {
            super(entityRenderer);
        }

        @Override
        public void render(PoseStack poseStack, T entity, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            List<IGeoLayerProvider> providers = ClientRenderRegistry.getLayerProviders(entity.getType());
            for (IGeoLayerProvider provider : providers) {
                provider.renderAdditionalLayer((GeoBaseRender) this.renderer,
                        entity, bakedModel, renderType, bufferSource, buffer, poseStack,
                        partialTick, packedLight, packedOverlay);
            }
        }
    }

    public void renderWithCustomTexture(PoseStack poseStack, T entity, BakedGeoModel model, RenderType renderType,
                                        MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                                        int packedLight, int packedOverlay, float r, float g, float b, float a) {
        this.actuallyRender(poseStack, entity, model, renderType, bufferSource, buffer, false,
                partialTick, packedLight, packedOverlay, r, g, b, a);
    }
}
