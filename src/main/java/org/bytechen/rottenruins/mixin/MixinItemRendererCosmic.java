package org.bytechen.rottenruins.mixin;

import org.bytechen.rottenruins.api.CosmicStyle;
import org.bytechen.rottenruins.api.ICosmicLayer;
import org.bytechen.rottenruins.api.mask.BlockMaskLayerItem;
import org.bytechen.rottenruins.api.mask.MaskLayerSpec;
import org.bytechen.rottenruins.client.cosmic.BakedModelCosmic;
import org.bytechen.rottenruins.client.cosmic.CosmicLayerRegistry;
import org.bytechen.rottenruins.client.cosmic.compat.CosmicItemLateRenderQueue;
import org.bytechen.rottenruins.client.cosmic.compat.CosmicItemShaderCompat;
import org.bytechen.rottenruins.client.cosmic.render.CosmicRenderType;
import org.bytechen.rottenruins.client.cosmic.render.CosmicRenderUtils;
import org.bytechen.rottenruins.client.cosmic.render.CosmicShaders;
import org.bytechen.rottenruins.client.mask.IMaskLayerCarrier;
import org.bytechen.rottenruins.client.mask.block.BlockMaskOverlayRenderer;
import org.bytechen.rottenruins.client.mask.MaskLayerRenderer;
import org.bytechen.rottenruins.client.mask.MaskLayerResolver;
import org.bytechen.rottenruins.client.mask.compat.MaskLayerLateRenderQueue;
import org.bytechen.rottenruins.client.mask.render.MaskLayerRenderUtils;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.client.rend.twitch.ITwitchItem;
import org.bytechen.rottenruins.client.rend.twitch.ItemTwitchHelper;
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
 * 在物品本体纹理之上叠加星空与崩坏（RGB 分离 + 扫描线）着色器层。
 * 顺序：本体 → 星空 → 效果层 → 崩坏；光影包激活时星空/崩坏改走
 * {@link CosmicItemLateRenderQueue} 帧末回放，直接写主帧缓冲。
 * 资格：星空来自 {@link BakedModelCosmic} 或 {@link ICosmicLayer} / {@link CosmicLayerRegistry}；
 * 崩坏来自 {@link BakedModelCosmic}（共用星空遮罩）或 {@link ITwitchItem}（用自身贴图当遮罩）。
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

        ResourceLocation corruptionMask = null;
        boolean hasCorruption = false;

        if (model instanceof BakedModelCosmic bc) {
            // 崩坏层的总闸必须放在这里：它是立即路径与延迟入队路径的共同上游，
            // 只看 renderCorruptionLayer 的门或只设 wrapper.setCorruptionMask() 都关不住。
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

        // 效果层两个来源：模型 JSON 声明的层（随 BakedModel 携带）+ 物品接口/注册表。
        List<MaskLayerSpec> jsonLayers = model instanceof IMaskLayerCarrier carrier
                ? carrier.maskLayers()
                : List.of();
        List<MaskLayerResolver.Resolved> maskLayers =
                MaskLayerResolver.resolve(stack, context, jsonLayers);

        // 方块物品的显示模型就是方块 cube，只能走方块侧「按模型 quad + UV 重映射」那套渲染。
        List<MaskLayerSpec> blockItemLayers = stack.getItem() instanceof BlockMaskLayerItem carrier
                ? carrier.maskLayers(stack)
                : List.of();

        // 着色器缺失时只降级对应层，不要整段 return —— 否则关联无关的层会被一起吞掉。
        if (cosmicMask != null && CosmicShaders.cosmicShader == null) cosmicMask = null;
        if (hasCorruption && CosmicShaders.corruptionShader == null) hasCorruption = false;
        if (cosmicMask == null && !hasCorruption && maskLayers.isEmpty()
                && blockItemLayers.isEmpty()) {
            return;
        }

        // 先把本体刷进 GPU 深度缓冲，后续 EQUAL 深度才叠得上去。
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        if (!CosmicShaders.cosmicInventoryRender
                && CosmicItemShaderCompat.shouldDeferItemShaderLayer(context)) {

            // 光影的阴影 pass 一律不入队：那一遍是从光源方向看物品背面，而我们的层是
            // 帧末按入队矩阵回放的，入队会把发光按光源视角盖回画面。判定沿用项目既有的
            // HeldItemOutlineCompat.isOculusShadowPass()（内部反射 IrisApi.isRenderingShadowPass）。
            if (HeldItemOutlineCompat.isOculusShadowPass()) return;

            if (cosmicMask != null || hasCorruption) {
                BakedModelCosmic wrapper;
                if (model instanceof BakedModelCosmic bc) {
                    wrapper = bc;
                } else {
                    // 只有真正解析出星空遮罩才开 cosmic；仅崩坏物品（ITwitchItem）必须关掉。
                    ResourceLocation primaryMask = cosmicMask != null ? cosmicMask : corruptionMask;
                    wrapper = new BakedModelCosmic(model, primaryMask);
                    wrapper.setCosmicEnabled(cosmicMask != null);
                    if (cosmicMask != null) {
                        wrapper.setOpacity(opacity);
                        wrapper.setStyle(CosmicStyle.fromShaderValue(styleVal));
                    }
                }
                if (hasCorruption) {
                    wrapper.setCorruptionMask(corruptionMask);
                } else {
                    wrapper.setCorruptionEnabled(false);
                }
                CosmicItemLateRenderQueue.enqueue(wrapper, stack, context, poseStack,
                        light, overlay, model);
            }

            // 效果层同样帧末回放；其 RenderType 带 MAIN_TARGET 输出，写主帧缓冲绕开光影 GBuffer。
            if (!maskLayers.isEmpty()) {
                MaskLayerLateRenderQueue.enqueue(stack, context, poseStack, light, overlay, maskLayers,
                        MaskLayerRenderUtils.baseSpriteOf(model));
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas blockAtlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

        // 方块物品表面层放在 cosmic 之前：它是 REPLACE 层，星空仍会以 EQUAL 深度画在它上面。
        if (!blockItemLayers.isEmpty()) {
            BlockMaskOverlayRenderer.renderItem(poseStack, buffer, light, overlay, model,
                    blockItemLayers, context, stack);
        }

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
            // 遮罩 sprite 在图集里的 UV 矩形，水面湍流那类 style 用它把图集 UV 折回 0..1；
            // 延迟回放路径在 BakedModelCosmic#renderShaderLayer 里另设一次，两处缺一不可。
            CosmicShaders.setMaskSlice(cosmicSprite);
            VertexConsumer cv = buffer.getBuffer(CosmicRenderType.COSMIC);
            mc.getItemRenderer().renderQuadList(poseStack, cv,
                    CosmicRenderUtils.bakeItem(cosmicSprite), stack, light, overlay);
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(CosmicRenderType.COSMIC);
            }
        }

        // 层序：本体 → 星空 → 效果层 → 崩坏（崩坏要保持在最上层的既有观感）；层内按 order 升序。
        MaskLayerRenderer.render(stack, context, poseStack, buffer, light, overlay,
                maskLayers, false, MaskLayerRenderUtils.baseSpriteOf(model));

        if (hasCorruption) {
            long gTime = mc.level != null ? mc.level.getGameTime() : 0;
            float intensity = ItemTwitchHelper.getTwitchIntensity(stack, gTime);
            if (intensity < 0.005f) return;

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

    /** 按 assets/&lt;namespace&gt;/textures/item/&lt;path&gt;.png 约定从物品注册名推崩坏遮罩贴图。 */
    private static ResourceLocation twitchCorruptionMask(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "item/missingno");
        }
        return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
    }
}
