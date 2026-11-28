package org.bytechen.rottenruins.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.api.IBlockingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 格挡姿势：原版只在 {@code getUseAnimation() == BLOCK} 时才给举盾手臂姿态，而 IBlockingWeapon 的武器必须返回
 * {@code NONE}（否则第一人称会走盾牌分支、武器整个消失）。这里在姿态解析的返回处补上 BLOCK，
 * 让第三人称与他人视角都有格挡姿势，第一人称仍由 {@code CrimsonVowBlockMixin} 自绘。
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerBlockPoseMixin {

    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true)
    private static void rottenruins$blockingPose(AbstractClientPlayer player, InteractionHand hand,
                                                 CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (player.getUsedItemHand() != hand || !IBlockingWeapon.isBlocking(player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof IBlockingWeapon) {
            cir.setReturnValue(HumanoidModel.ArmPose.BLOCK);
        }
    }
}
