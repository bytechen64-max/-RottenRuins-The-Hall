package org.bytechen.hall.mixin;

import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.api.ICosmicLayer;
import org.bytechen.hall.client.cosmic.BakedModelCosmic;
import org.bytechen.hall.client.cosmic.CosmicLayerRegistry;
import org.bytechen.hall.client.cosmic.compat.CosmicItemLateRenderQueue;
import org.bytechen.hall.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import org.bytechen.hall.client.rend.twitch.ItemTwitchHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders cosmic starfield and/or corruption (RGB split + scanlines)
 * shader layers on top of the base item texture.
 *
 * <h3>Render order</h3>
 * <ol>
 *   <li>Vanilla renders base item model</li>
 *   <li>Here: cosmic starfield layer (EQUAL depth)</li>
 *   <li>Here: corruption layer on top (EQUAL depth)</li>
 *   <li>ItemRendererMixin: outline + glint (ADDITIVE blend)</li>
 * </ol>
 *
 * <h3>Shader pack compatibility</h3>
 * When Oculus/Iris is active, both cosmic and corruption layers are
 * deferred to {@link CosmicItemLateRenderQueue} and replayed after
 * the world render completes, writing directly to the main framebuffer.
 *
 * <h3>Layer eligibility</h3>
 * <ul>
 *   <li><b>Cosmic</b>: {@link BakedModelCosmic} model, or
 *       {@link ICosmicLayer} on item / in {@link CosmicLayerRegistry}</li>
 *   <li><b>Corruption</b>: {@link BakedModelCosmic} model (uses same mask
 *       as cosmic), or {@link ITwitchItem} (uses item's own texture as mask)</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererCosmic {

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                     shift = At.Shift.BEFORE))
    private void renderShaderLayers(ItemStack stack, ItemDisplayContext context,
                                    boolean leftHand, PoseStack poseStack,
                                    MultiBufferSource buffer, int light, int overlay,
                                    BakedModel model, CallbackInfo ci) {
        // ── Resolve cosmic eligibility ──
        ResourceLocation cosmicMask = null;
        float opacity = 1.0f;
        int styleVal = 0;

        if (model instanceof BakedModelCosmic bc) {
            cosmicMask = bc.getMaskTexture();
            opacity = bc.getOpacity() >= 0f ? bc.getOpacity() : 1.0f;
            styleVal = bc.getStyle() != null ? bc.getStyle().shaderValue : 0;
        } else {
            ICosmicLayer config = CosmicLayerRegistry.resolve(stack);
            if (config != null && config.cosmicShouldRender(context)) {
                cosmicMask = config.cosmicMask();
                opacity = config.cosmicOpacity();
                styleVal = config.cosmicStyle().shaderValue;
            }
        }

        // ── Resolve corruption eligibility ──
        ResourceLocation corruptionMask = null;
        boolean hasCorruption = false;

        if (model instanceof BakedModelCosmic bc) {
            corruptionMask = bc.getCorruptionMask();
            hasCorruption = true;
        } else if (stack.getItem() instanceof ITwitchItem ti) {
            if (ti.twitchShouldRender(context)) {
                corruptionMask = twitchCorruptionMask(stack);
                hasCorruption = true;
            }
        }

        // ── Neither cosmic nor corruption → nothing to do ──
        if (cosmicMask == null && !hasCorruption) return;
        // Cosmic shader must be loaded for cosmic; corruption shader must be loaded for corruption
        if (cosmicMask != null && CosmicShaders.cosmicShader == null) return;
        if (hasCorruption && CosmicShaders.corruptionShader == null) return;

        // ── Flush base item so depth is in the GPU depth buffer ──
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        // ── Shader pack active → defer both layers ──
        if (!CosmicShaders.cosmicInventoryRender
                && CosmicItemShaderCompat.shouldDeferItemShaderLayer(context)) {

            BakedModelCosmic wrapper;
            if (model instanceof BakedModelCosmic bc) {
                wrapper = bc;
            } else {
                // Create a wrapper for the queue.  Only enable cosmic
                // if we actually resolved a cosmic mask — otherwise this is
                // a corruption-only item (ITwitchItem) and cosmic must stay off.
                ResourceLocation primaryMask = cosmicMask != null ? cosmicMask : corruptionMask;
                wrapper = new BakedModelCosmic(model, primaryMask);
                wrapper.setCosmicEnabled(cosmicMask != null);
                if (cosmicMask != null) {
                    wrapper.setOpacity(opacity);
                    wrapper.setStyle(CosmicStyle.fromShaderValue(styleVal));
                }
            }
            // Enable corruption layer if applicable
            if (hasCorruption) {
                wrapper.setCorruptionMask(corruptionMask);
            } else {
                wrapper.setCorruptionEnabled(false);
            }
            CosmicItemLateRenderQueue.enqueue(wrapper, stack, context, poseStack,
                    light, overlay, model);
            return;
        }

        // ── Immediate rendering (no shader pack, or GUI) ──

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas blockAtlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

        // ── Cosmic layer ──
        if (cosmicMask != null) {
            CosmicShaders.markCosmicSpritesActive();

            float yaw = 0, pitch = 0, wscale = 1;
            if (CosmicShaders.cosmicInventoryRender || context == ItemDisplayContext.GUI)
                wscale = 100.0F;
            else if (mc.player != null) {
                yaw   =  (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
                pitch = -(float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0);
            }

            if (CosmicShaders.timeUniform != null)
                CosmicShaders.timeUniform.set((float) (mc.level.getGameTime() % Integer.MAX_VALUE));
            if (CosmicShaders.yawUniform != null)         CosmicShaders.yawUniform.set(yaw);
            if (CosmicShaders.pitchUniform != null)       CosmicShaders.pitchUniform.set(pitch);
            if (CosmicShaders.externalScaleUniform != null) CosmicShaders.externalScaleUniform.set(wscale);
            if (CosmicShaders.opacityUniform != null)     CosmicShaders.opacityUniform.set(opacity);
            if (CosmicShaders.useTypeUniform != null)     CosmicShaders.useTypeUniform.set(styleVal);
            if (CosmicShaders.cosmicuvsUniform != null)   CosmicShaders.cosmicuvsUniform.set(CosmicShaders.COSMIC_UVS);

            TextureAtlasSprite cosmicSprite = blockAtlas.getSprite(cosmicMask);
            VertexConsumer cv = buffer.getBuffer(CosmicRenderType.COSMIC);
            mc.getItemRenderer().renderQuadList(poseStack, cv,
                    CosmicRenderUtils.bakeItem(cosmicSprite), stack, light, overlay);
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(CosmicRenderType.COSMIC);
            }
        }

        // ── Corruption layer (only during twitch burst) ──
        if (hasCorruption) {
            long gTime = mc.level != null ? mc.level.getGameTime() : 0;
            float intensity = ItemTwitchHelper.getTwitchIntensity(stack, gTime);
            if (intensity < 0.005f) return; // outside burst — skip

            float gameTime = (float) (gTime % Integer.MAX_VALUE);
            if (CosmicShaders.corruptionTimeUniform != null)
                CosmicShaders.corruptionTimeUniform.set(gameTime);
            if (CosmicShaders.corruptionIntensityUniform != null)
                CosmicShaders.corruptionIntensityUniform.set(intensity);

            TextureAtlasSprite corruptionSprite = blockAtlas.getSprite(corruptionMask);
            VertexConsumer v = buffer.getBuffer(CosmicRenderType.CORRUPTION);
            mc.getItemRenderer().renderQuadList(poseStack, v,
                    CosmicRenderUtils.bakeItem(corruptionSprite), stack, light, overlay);
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(CosmicRenderType.CORRUPTION);
            }
        }
    }

    /**
     * Derive a corruption mask ResourceLocation from the item's registry name,
     * following the standard {@code assets/<namespace>/textures/item/<path>.png}
     * convention.
     */
    private static ResourceLocation twitchCorruptionMask(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "item/missingno");
        }
        return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
    }
}
