package org.bytechen.hall.client.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.bytechen.hall.client.entity.IAutoRenderableEntity;
import org.bytechen.hall.client.entity.IGeoLayerProvider;
import org.bytechen.hall.client.entity.IGeoModelBehavior;
import org.bytechen.hall.client.entity.model.GeoBaseModel;
import org.bytechen.hall.overworld.registry.ClientRenderRegistry;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
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
        // push/pop 把爆体缩放限制在"模型 + 所有 render layer"内：
        // 不弹出的话，后续 EntityRenderDispatcher 画的影子/火焰/名牌/命中框
        // 也会继承这个缩放（影子变大通常无妨，但名牌与命中框会明显不对）。
        poseStack.pushPose();
        try {
            applyBurstScale(animatable, poseStack, partialTick);
            super.render(animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 爆体阶梯脉冲缩放 —— 纯视觉，只动渲染矩阵，不动碰撞箱。
     *
     * <p>缩放在 {@code super.render} <b>之前</b>乘进 pose：此时 pose 已经由
     * {@code EntityRenderDispatcher} 平移到实体脚底，所以缩放是"以脚底为原点"
     * 地整个模型变大 —— 脚不会陷进地面，也不会因为从模型中心缩放而上下飘。
     * 之后 GeckoLib 的模型、所有 render layer（包括自定义贴图层）都会继承这个缩放；
     * 外层 {@code push/pop} 保证它不会泄漏到实体渲染器之后的绘制。</p>
     *
     * <p>没有在 {@code AbstractHallEntity} 里直接调 {@code PoseStack}，是为了让实体类
     * 保持纯逻辑；数学入口见 {@link AbstractHallEntity#getBurstScale(float)}。</p>
     */
    private void applyBurstScale(T animatable, PoseStack poseStack, float partialTick) {
        if (!(animatable instanceof AbstractHallEntity hall)) return;
        float scale = hall.getBurstScale(partialTick);
        if (Math.abs(scale - 1.0f) < 1.0e-4f) return;
        poseStack.scale(scale, scale, scale);
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
