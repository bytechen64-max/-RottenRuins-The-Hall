package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.bytechen.hall.client.rend.twitch.ItemTwitchHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BakedModelCosmic extends BakedModelRendererBase {

    private final ResourceLocation maskTexture;
    private ResourceLocation corruptionMaskTexture;
    private float opacityOverride = -1.0f;
    private CosmicStyle styleOverride = null;
    private boolean cosmicEnabled = true;
    private boolean corruptionEnabled = false;

    public BakedModelCosmic(BakedModel inner, ResourceLocation maskTexture) {
        super(inner); this.maskTexture = maskTexture;
    }

    public void setOpacity(float v) { this.opacityOverride = v; }
    public float getOpacity() { return opacityOverride; }
    public void setStyle(CosmicStyle s) { this.styleOverride = s; }
    public CosmicStyle getStyle() { return styleOverride; }
    public ResourceLocation getMaskTexture() { return maskTexture; }

    public void setCorruptionMask(ResourceLocation mask) { this.corruptionMaskTexture = mask; this.corruptionEnabled = true; }
    public ResourceLocation getCorruptionMask() { return corruptionMaskTexture != null ? corruptionMaskTexture : maskTexture; }

    /** Whether cosmic starfield rendering should happen for this model. */
    public boolean isCosmicEnabled() { return cosmicEnabled; }
    public void setCosmicEnabled(boolean v) { this.cosmicEnabled = v; }

    /** Whether corruption (RGB split) rendering should happen for this model. */
    public boolean isCorruptionEnabled() { return corruptionEnabled; }
    public void setCorruptionEnabled(boolean v) { this.corruptionEnabled = v; }

    @Override public boolean isCustomRenderer() { return false; }

    /** No-op: vanilla renders quads via getQuads() → inner. */
    @Override
    public void render(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                       PoseStack ps, MultiBufferSource buf, int light, int overlay,
                       BakedModel model) {}

    /** Render cosmic layer directly (used by CosmicItemLateRenderQueue). */
    public void renderShaderLayer(ItemStack stack, ItemDisplayContext ctx,
                                  PoseStack ps, MultiBufferSource buf,
                                  int light, int overlay, BakedModel model,
                                  boolean lateRender) {
        if (!cosmicEnabled) return;
        if (CosmicShaders.cosmicShader == null) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        CosmicShaders.markCosmicSpritesActive();

        float yaw = 0, pitch = 0, scale = 1;
        if (CosmicShaders.cosmicInventoryRender || ctx == ItemDisplayContext.GUI)
            scale = 100.0F;
        else if (mc.player != null) {
            yaw   =  (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
            pitch = -(float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0);
        }

        if (CosmicShaders.timeUniform != null)
            CosmicShaders.timeUniform.set((float) (mc.level.getGameTime() % Integer.MAX_VALUE));
        if (CosmicShaders.yawUniform != null)         CosmicShaders.yawUniform.set(yaw);
        if (CosmicShaders.pitchUniform != null)       CosmicShaders.pitchUniform.set(pitch);
        if (CosmicShaders.externalScaleUniform != null) CosmicShaders.externalScaleUniform.set(scale);
        if (CosmicShaders.opacityUniform != null)
            CosmicShaders.opacityUniform.set(opacityOverride >= 0f ? opacityOverride : 1.0F);
        if (CosmicShaders.useTypeUniform != null)
            CosmicShaders.useTypeUniform.set(styleOverride != null ? styleOverride.shaderValue : 0);
        if (CosmicShaders.cosmicuvsUniform != null)
            CosmicShaders.cosmicuvsUniform.set(CosmicShaders.COSMIC_UVS);

        RenderType rt = lateRender ? lateRenderType(ctx) : CosmicRenderType.COSMIC;
        TextureAtlasSprite sprite = atlas.getSprite(maskTexture);
        VertexConsumer vc = buf.getBuffer(rt);
        mc.getItemRenderer().renderQuadList(ps, vc,
                CosmicRenderUtils.bakeItem(sprite), stack, light, overlay);
        if (!lateRender && buf instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(rt);
    }

    private static RenderType lateRenderType(ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return fp ? CosmicRenderType.COSMIC_HAND_AFTER_LEVEL
                : CosmicRenderType.COSMIC_AFTER_LEVEL;
    }

    private static RenderType lateCorruptionRenderType(ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return fp ? CosmicRenderType.CORRUPTION_HAND_AFTER_LEVEL
                : CosmicRenderType.CORRUPTION_AFTER_LEVEL;
    }

    /**
     * Render the corruption layer (RGB split + scanlines + colour shift)
     * on top of the base item texture, using the cosmic mask.
     *
     * @param lateRender true when called from {@link org.bytechen.hall.client.cosmic.compat.CosmicItemLateRenderQueue}
     */
    public void renderCorruptionLayer(ItemStack stack, ItemDisplayContext ctx,
                                      PoseStack ps, MultiBufferSource buf,
                                      int light, int overlay, BakedModel model,
                                      boolean lateRender) {
        if (!corruptionEnabled) return;
        if (CosmicShaders.corruptionShader == null) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

        float gameTime = mc.level != null ? (float) (mc.level.getGameTime() % Integer.MAX_VALUE) : 0f;
        if (CosmicShaders.corruptionTimeUniform != null)
            CosmicShaders.corruptionTimeUniform.set(gameTime);
        float intensity = ItemTwitchHelper.getTwitchIntensity(stack, mc.level != null ? mc.level.getGameTime() : 0);
        if (intensity < 0.005f) return; // outside burst — skip corruption entirely
        if (CosmicShaders.corruptionIntensityUniform != null)
            CosmicShaders.corruptionIntensityUniform.set(intensity);

        RenderType rt = lateRender ? lateCorruptionRenderType(ctx) : CosmicRenderType.CORRUPTION;
        TextureAtlasSprite maskSprite = atlas.getSprite(getCorruptionMask());
        VertexConsumer vc = buf.getBuffer(rt);
        mc.getItemRenderer().renderQuadList(ps, vc,
                CosmicRenderUtils.bakeItem(maskSprite), stack, light, overlay);

        if (!lateRender && buf instanceof MultiBufferSource.BufferSource bs)
            bs.endBatch(rt);
    }
}
