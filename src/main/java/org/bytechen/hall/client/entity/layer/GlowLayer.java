package org.bytechen.hall.client.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.entity.IGeoLayerProvider;
import org.bytechen.hall.client.entity.render.GeoBaseRender;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class GlowLayer implements IGeoLayerProvider {

    private final ResourceLocation glowTexture;

    public GlowLayer(ResourceLocation glowTexture) {
        this.glowTexture = glowTexture;
    }

    public GlowLayer(String texturePath) {
        this(ResourceLocation.fromNamespaceAndPath(HallMod.MODID, texturePath));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void renderAdditionalLayer(GeoBaseRender<?> renderer, LivingEntity entity,
                                      BakedGeoModel bakedModel, RenderType renderType,
                                      MultiBufferSource bufferSource, VertexConsumer buffer,
                                      PoseStack poseStack, float partialTick,
                                      int packedLight, int packedOverlay) {

        RenderType emissiveType = RenderType.eyes(glowTexture);
        VertexConsumer glowBuffer = bufferSource.getBuffer(emissiveType);

        ((GeoBaseRender) renderer).renderWithCustomTexture(
                poseStack, entity, bakedModel, emissiveType,
                bufferSource, glowBuffer, partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay,
                1.0f, 1.0f, 1.0f, 1.0f);
    }
}
