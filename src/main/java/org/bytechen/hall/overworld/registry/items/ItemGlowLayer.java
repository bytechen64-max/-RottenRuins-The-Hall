package org.bytechen.hall.overworld.registry.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Emissive glow render layer for Geo items.
 * <p>
 * Renders a second pass over the baked geo model using {@link RenderType#eyes}
 * (full-bright, additive blending), analogous to the entity {@code GlowLayer}.
 * Uses {@link GeoRenderer#reRender} for the second pass.
 * </p>
 *
 * @param <T> the item type, extending Item & GeoAnimatable
 */
public class ItemGlowLayer<T extends Item & GeoAnimatable> extends GeoRenderLayer<T> {

    private final ResourceLocation glowTexture;

    /**
     * @param renderer     the parent GeoRenderer
     * @param glowTexture  the glow/emissive texture ResourceLocation
     */
    public ItemGlowLayer(GeoRenderer<T> renderer, ResourceLocation glowTexture) {
        super(renderer);
        this.glowTexture = glowTexture;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        RenderType emissiveType = RenderType.eyes(glowTexture);
        VertexConsumer glowBuffer = bufferSource.getBuffer(emissiveType);

        this.renderer.reRender(
                bakedModel, poseStack, bufferSource, animatable,
                emissiveType, glowBuffer, partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay,
                1.0f, 1.0f, 1.0f, 1.0f);
    }
}
