package org.bytechen.hall.mixin;

import org.bytechen.hall.api.CosmicStyle;
import org.bytechen.hall.api.ICosmicLayer;
import org.bytechen.hall.api.mask.MaskLayerSpec;
import org.bytechen.hall.client.cosmic.BakedModelCosmic;
import org.bytechen.hall.client.cosmic.CosmicLayerRegistry;
import org.bytechen.hall.client.cosmic.compat.CosmicItemLateRenderQueue;
import org.bytechen.hall.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.hall.client.cosmic.render.CosmicRenderType;
import org.bytechen.hall.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.hall.client.cosmic.render.CosmicShaders;
import org.bytechen.hall.client.mask.IMaskLayerCarrier;
import org.bytechen.hall.client.mask.MaskLayerRenderer;
import org.bytechen.hall.client.mask.MaskLayerResolver;
import org.bytechen.hall.client.mask.compat.MaskLayerLateRenderQueue;
import org.bytechen.hall.client.mask.render.MaskLayerRenderUtils;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
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

import java.util.List;

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
            // 关键：BakedModelCosmic 不一定想要崩坏层。
            // 之前这里无条件 hasCorruption = true，于是：
            //   • 立即路径（下方直接 getBuffer(CORRUPTION) 那段）会照画不误，
            //     因为它根本不经过 renderCorruptionLayer，那道 corruptionEnabled 门够不着；
            //   • 延迟路径入队时 wrapper.setCorruptionMask() 又会把开关重新打开。
            // 所以崩坏层的总闸必须在这里——所有路径的共同上游。
            boolean corruptionAllowed = bc.isCorruptionEnabled()
                    && !(stack.getItem() instanceof ITwitchItem ti && ti.twitchDisabled());
            if (corruptionAllowed) {
                corruptionMask = bc.getCorruptionMask();
                hasCorruption = true;
            }
        } else if (stack.getItem() instanceof ITwitchItem ti) {
            if (ti.twitchShouldRender(context)) {
                corruptionMask = twitchCorruptionMask(stack);
                hasCorruption = true;
            }
        }

        // ── Resolve mask effect layers（叠加在星空之上的独立效果层）──
        // 两个来源：模型 JSON 声明的层（由 BakedModel 携带）+ 物品接口/注册表。
        List<MaskLayerSpec> jsonLayers = model instanceof IMaskLayerCarrier carrier
                ? carrier.maskLayers()
                : List.of();
        List<MaskLayerResolver.Resolved> maskLayers =
                MaskLayerResolver.resolve(stack, context, jsonLayers);

        // ── Neither cosmic nor corruption nor mask layers → nothing to do ──
        // 这里刻意用「降级」而不是「整段 return」：原来 cosmic 着色器一加载失败就会
        // 直接返回，把跟它毫无关系的崩坏层和效果层一起吞掉 —— 症状是「改了个泛光
        // 层，星空和崩坏一起不见了」，因果完全对不上。
        if (cosmicMask != null && CosmicShaders.cosmicShader == null) cosmicMask = null;
        if (hasCorruption && CosmicShaders.corruptionShader == null) hasCorruption = false;
        if (cosmicMask == null && !hasCorruption && maskLayers.isEmpty()) return;

        // ── Flush base item so depth is in the GPU depth buffer ──
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        // ── Shader pack active → defer both layers ──
        if (!CosmicShaders.cosmicInventoryRender
                && CosmicItemShaderCompat.shouldDeferItemShaderLayer(context)) {

            // 光影的**阴影 pass** 里一律不入队。
            // 用项目既有的 HeldItemOutlineCompat.isOculusShadowPass()（内部反射
            // IrisApi.isRenderingShadowPass）—— BlackHole / Shockwave / CollapsarHalo /
            // ApostleSlashWarp 四个延迟渲染器都是这个写法，见 docs/shader-pack-compat.md 第 361 行。
            // 阴影那一遍是从光源方向看的（看到的是物品的"背面"），我们的层却是帧末按
            // 入队时的矩阵回放的，让它入队就会把发光按光源视角盖回画面。
            if (HeldItemOutlineCompat.isOculusShadowPass()) return;

            // cosmic / 崩坏层的入队逻辑保持原样，只是被套进「确实有东西要画」的判断里
            // —— 一件只有 mask 效果层的物品不该被塞一个空 wrapper 进队列。
            if (cosmicMask != null || hasCorruption) {
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
            }

            // 效果层同样延迟回放。它们各自的 RenderType 带 MAIN_TARGET 输出，
            // 所以在这一相位写的是主帧缓冲，绕开了光影包的 GBuffer。
            if (!maskLayers.isEmpty()) {
                MaskLayerLateRenderQueue.enqueue(stack, context, poseStack, light, overlay, maskLayers,
                        MaskLayerRenderUtils.baseSpriteOf(model));
            }
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
            // 遮罩 sprite 在图集里的 UV 矩形（水面湍流那道 style 用它把图集 UV 折回 0..1）。
            // 延迟回放那条路在 BakedModelCosmic#renderShaderLayer 里另设一次，两处缺一不可。
            CosmicShaders.setMaskSlice(cosmicSprite);
            VertexConsumer cv = buffer.getBuffer(CosmicRenderType.COSMIC);
            mc.getItemRenderer().renderQuadList(poseStack, cv,
                    CosmicRenderUtils.bakeItem(cosmicSprite), stack, light, overlay);
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(CosmicRenderType.COSMIC);
            }
        }

        // ── Mask effect layers ──
        // 层序：本体 → 星空 → 效果层 → 崩坏。效果层排在崩坏之前，是为了保住
        // 「崩坏是故障爆发时整片覆盖」的既有观感（它一直是最上面那层）。
        // 层与层之间再按各自的 order 升序。
        MaskLayerRenderer.render(stack, context, poseStack, buffer, light, overlay,
                maskLayers, false, MaskLayerRenderUtils.baseSpriteOf(model));

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
