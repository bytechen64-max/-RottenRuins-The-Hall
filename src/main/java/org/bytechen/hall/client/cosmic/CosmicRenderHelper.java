package org.bytechen.hall.client.cosmic;

import org.bytechen.hall.api.ICosmicLayer;
import org.bytechen.hall.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Shared cosmic layer rendering logic, usable from both
 * {@link BakedModelCosmic} (JSON model loader path) and
 * {@link MixinItemRendererCosmic} (registry/interface path).
 *
 * <p>This class extracts the common "render item base → flush → render
 * cosmic shader layer on top" sequence so it can be reused without
 * needing a BakedModelCosmic wrapper instance.</p>
 */
public final class CosmicRenderHelper {

    private CosmicRenderHelper() {}

    /**
     * Render the full cosmic item: base model (depth write) followed by
     * cosmic starfield layer (EQUAL depth, shader overlay).
     *
     * <p>This is the code path used when an item is resolved via
     * {@link ICosmicLayer} (interface or manual registry) rather than
     * the JSON {@code "splendiding:cosmic"} model loader.  The logic is
     * identical to {@link BakedModelCosmic#render}.</p>
     *
     * @param stack       the item stack
     * @param context     display context
     * @param leftHand    whether rendered in the left hand
     * @param poseStack   current pose stack
     * @param bufferSource the active buffer source
     * @param packedLight  packed lightmap coordinates
     * @param packedOverlay packed overlay coordinates
     * @param model       the baked model (base item model, not wrapped)
     * @param config      the resolved cosmic config (from interface or registry)
     */
    public static void renderCosmicItem(ItemStack stack, ItemDisplayContext context,
                                        boolean leftHand, PoseStack poseStack,
                                        MultiBufferSource bufferSource,
                                        int packedLight, int packedOverlay,
                                        BakedModel model, ICosmicLayer config) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();

        // 1. Render the base item texture normally (writes depth)
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                itemRenderer.renderModelLists(pass, stack, packedLight, packedOverlay,
                        poseStack, bufferSource.getBuffer(renderType));
            }
        }

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        // 2. Shader pack compatibility decision
        if (!CosmicShaders.cosmicInventoryRender
                && CosmicItemShaderCompat.shouldDeferItemShaderLayer(context)) {
            // For deferred rendering, we can't use CosmicItemLateRenderQueue directly
            // because it requires a BakedModelCosmic.  Instead we fall through to
            // immediate rendering with the cosmic shader — in practice, items that
            // use the registry path are rare and the worst case is the cosmic layer
            // being captured into the GBuffer (same as what would happen without
            // any compat code).  Full deferred rendering requires the JSON model
            // loader path.
            //
            // Users who need full shader compat for a specific item should use the
            // JSON model loader or create a BakedModel wrapper via
            // ModelEvent.BakingCompleted.
        }

        // 3. Render cosmic layer
        renderCosmicLayer(context, poseStack, bufferSource, config);
    }

    /**
     * Render only the cosmic shader layer (assumes base model is already drawn).
     * Used directly by {@link BakedModelCosmic#renderShaderLayer}.
     */
    public static void renderCosmicLayer(ItemDisplayContext context, PoseStack poseStack,
                                         MultiBufferSource bufferSource, ICosmicLayer config) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        TextureAtlas blockAtlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

        CosmicShaders.markCosmicSpritesActive();

        float yaw   = 0.0F;
        float pitch = 0.0F;
        float scale = 1.0F;

        if (CosmicShaders.cosmicInventoryRender || context == ItemDisplayContext.GUI) {
            scale = 100.0F;
        } else if (mc.player != null) {
            yaw   =  (float) (mc.player.getYRot() * 2.0F * Math.PI / 360.0);
            pitch = -(float) (mc.player.getXRot() * 2.0F * Math.PI / 360.0);
        }

        if (CosmicShaders.timeUniform != null) {
            float timeValue = (float) (mc.level.getGameTime() % Integer.MAX_VALUE);
            CosmicShaders.timeUniform.set(timeValue);
        }
        if (CosmicShaders.yawUniform != null)         CosmicShaders.yawUniform.set(yaw);
        if (CosmicShaders.pitchUniform != null)       CosmicShaders.pitchUniform.set(pitch);
        if (CosmicShaders.externalScaleUniform != null) CosmicShaders.externalScaleUniform.set(scale);
        if (CosmicShaders.cosmicuvsUniform != null)   CosmicShaders.cosmicuvsUniform.set(CosmicShaders.COSMIC_UVS);

        ResourceLocation maskLoc = config.cosmicMask();
        float opacity = config.cosmicOpacity();

        if (CosmicShaders.opacityUniform != null)
            CosmicShaders.opacityUniform.set(opacity);
        if (CosmicShaders.useTypeUniform != null)
            CosmicShaders.useTypeUniform.set(config.cosmicStyle().shaderValue);

        TextureAtlasSprite maskSprite = blockAtlas.getSprite(maskLoc);
        // 遮罩 sprite 在图集里的 UV 矩形（水面湍流那道 style 用它把图集 UV 折回 0..1）。
        // 这条路径目前没有调用方，但它是 ICosmicLayer 的公开入口 —— 少这一行的话，
        // 以后谁走它谁就会拿到"图案糊成一片"的 18 号样式，且症状极难反查。
        CosmicShaders.setMaskSlice(maskSprite);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(CosmicRenderType.COSMIC);
        itemRenderer.renderQuadList(poseStack, vertexConsumer,
                CosmicRenderUtils.bakeItem(maskSprite), ItemStack.EMPTY, 0, 0);

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(CosmicRenderType.COSMIC);
        }
    }
}
