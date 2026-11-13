package org.bytechen.rottenruins.mixin;

import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.bytechen.rottenruins.client.rend.CrimsonVowBlockRig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 低版本（1.8 式）举剑格挡的客户端接入点：原版照常渲染，只在 PoseStack 上叠格挡增量。
 *  两个注入：applyBlockDelta 就地改传入的 PoseStack；markCapture 补描边捕获标记。 */
@Mixin(value = ItemInHandRenderer.class, priority = 890)
public abstract class CrimsonVowBlockMixin {

    /** 在 ItemInHandRenderer.renderItem（SRG m_269530_）INVOKE 之前就地改 poseStack：
     *  必须用 @Inject 而非 @ModifyArg（后者报 invalid signature）；此刻栈上已是"正常手持"。
     */
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
                            + "renderItem(Lnet/minecraft/world/entity/LivingEntity;"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0)
    private void crimsonvow$applyBlockDelta(AbstractClientPlayer player, float partialTick, float pitch,
                                            InteractionHand hand, float swingProgress, ItemStack stack,
                                            float equipProgress, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            CallbackInfo ci) {
        if (!CrimsonVowBlockRig.shouldApply(player, stack, hand)) return;
        // 左手（副手举剑 / 左手玩家的主手）要镜像，否则增量会往屏幕外推、剑面转反
        CrimsonVowBlockRig.applyBlockDelta(poseStack, CrimsonVowBlockRig.rendersOnLeftSide(player, hand));
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void crimsonvow$markCapture(AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight,
                                        CallbackInfo ci) {
        if (!CrimsonVowBlockRig.shouldApply(player, stack, hand)) return;
        if (org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineRenderer.isCaptureActive()
                && !org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineRenderer.shouldSkipHand(hand)) {
            org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineRenderer.markHandCaptured();
        }
    }

    /** 供调试：当前是否命中格挡（未使用，保留给后续排查）。 */
    @SuppressWarnings("unused")
    private static boolean isBlockingWeapon(ItemStack stack) {
        return stack.getItem() instanceof IBlockingWeapon;
    }
}
