package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.AbstractItemRenderHelper;
import org.bytechen.hall.client.rend.ItemRenderManager;
import org.bytechen.hall.client.rend.glint.OutlineGlintRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    private List<AbstractItemRenderHelper> appliedWrapHelpers = null;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderItem(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                              PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                              int combinedOverlay, BakedModel model, CallbackInfo ci) {
        appliedWrapHelpers = ItemRenderManager.getInstance().applyWrapTransforms(
                poseStack, stack, context, buffer, combinedLight, combinedOverlay);

        ItemRenderManager.getInstance().renderAttachedEffects(
                poseStack, stack, context, buffer, combinedLight, combinedOverlay);
    }

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                     shift = At.Shift.BEFORE))
    private void onBeforePopPose(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                 PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                 int combinedOverlay, BakedModel model, CallbackInfo ci) {
        OutlineGlintRenderer.renderGlintPass(stack, context, poseStack, buffer, combinedLight, combinedOverlay, model);
        OutlineGlintRenderer.renderWorldOutlinePass(stack, context, poseStack, buffer, combinedLight, combinedOverlay, model);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderItemReturn(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                    PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                    int combinedOverlay, BakedModel model, CallbackInfo ci) {
        if (appliedWrapHelpers != null) {
            try {
                ItemRenderManager.getInstance().restoreWrapTransforms(poseStack, appliedWrapHelpers);
            } catch (ClassCastException e) {
                // 与其他模组（如 corruption）的 mixin 字段冲突时，
                // appliedWrapHelpers 中可能混入非 AbstractItemRenderHelper 对象，
                // 静默忽略以避免游戏崩溃
            }
            appliedWrapHelpers = null;
        }
    }
}
