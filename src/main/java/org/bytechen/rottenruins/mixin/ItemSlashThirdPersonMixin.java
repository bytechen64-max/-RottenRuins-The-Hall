package org.bytechen.rottenruins.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.client.rend.slash.ItemSlashAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemSlashThirdPersonMixin {
    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;

    // At rest return immediately so vanilla owns the complete hand pose.
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void rottenruins$moveHeldSword(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                          HumanoidArm arm, PoseStack poses, MultiBufferSource buffers,
                                          int light, CallbackInfo ci) {
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var pose = ItemSlashAnimation.pose(entity, hand, stack, Minecraft.getInstance().getFrameTime());
        if (pose == null) return;
        boolean left = arm == HumanoidArm.LEFT;
        PoseStack idle = new PoseStack();
        idle.last().pose().set(poses.last().pose());
        ((ArmedModel) ((ItemInHandLayer<?, ?>) (Object) this).getParentModel()).translateToHand(arm, idle);
        idle.mulPose(Axis.XP.rotationDegrees(-90));
        idle.mulPose(Axis.YP.rotationDegrees(180));
        idle.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        boolean rendered = false;
        poses.pushPose();
        try {
            if (ItemSlashAnimation.apply(poses, entity, stack, context, left, pose, 0, idle.last().pose())) {
                itemInHandRenderer.renderItem(entity, stack, context, left, poses, buffers, light);
                rendered = true;
            }
        } finally {
            poses.popPose();
        }
        if (rendered) ci.cancel();
    }
}
