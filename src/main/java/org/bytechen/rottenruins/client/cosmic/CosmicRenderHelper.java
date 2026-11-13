package org.bytechen.rottenruins.client.cosmic;

import org.bytechen.rottenruins.api.ICosmicLayer;
import org.bytechen.rottenruins.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.rottenruins.client.cosmic.render.CosmicRenderType;
import org.bytechen.rottenruins.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.rottenruins.client.cosmic.render.CosmicShaders;
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

/** Shared cosmic layer rendering: base model → flush → cosmic shader layer.
 *  Reused by {@link BakedModelCosmic} (JSON loader) and {@link MixinItemRendererCosmic}. */
public final class CosmicRenderHelper {

    private CosmicRenderHelper() {}

    /** Render the full cosmic item: base model (writes depth) then cosmic
     *  starfield layer (EQUAL depth, shader overlay). Same logic as
     *  {@link BakedModelCosmic#render}, for the {@link ICosmicLayer} path. */
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
            // 全延迟渲染需要 BakedModelCosmic 包装，注册表路径没有，只能就地渲染：
            // 宇宙层可能被写进 GBuffer。要完整兼容请改用 JSON 模型加载器。
        }

        renderCosmicLayer(context, poseStack, bufferSource, config);
    }

    /** Render only the cosmic shader layer (base model already drawn).
     *  Used by {@link BakedModelCosmic#renderShaderLayer}. */
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
        // 遮罩 sprite 在图集里的 UV 矩形：水面湍流 style 靠它把图集 UV 折回 0..1，
        // 少这一行会退化成"图案糊成一片"且极难反查。
        CosmicShaders.setMaskSlice(maskSprite);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(CosmicRenderType.COSMIC);
        itemRenderer.renderQuadList(poseStack, vertexConsumer,
                CosmicRenderUtils.bakeItem(maskSprite), ItemStack.EMPTY, 0, 0);

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(CosmicRenderType.COSMIC);
        }
    }
}
