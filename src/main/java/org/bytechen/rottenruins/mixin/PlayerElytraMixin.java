package org.bytechen.rottenruins.mixin;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 圣翼：带效果时绕开 {@code updateFallFlying} 里的鞘翅物品判定。
 *
 * <p>展翼规则刻意做成原版鞘翅那样"先离地、再按跳跃才展翼"：站在地上按跳跃
 * 不会进入飞行（之前是无条件展翼，导致站在地面也频繁抖进鞘翅状态）。</p>
 */
@Mixin(LivingEntity.class)
public abstract class PlayerElytraMixin {

    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void rottenruins$holyWings(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)
                || !CustomEffects.hasEffect(player, RegisterCustomEffect.HOLY_WINGS)) {
            return;
        }
        boolean airborne = !player.onGround() && !player.isPassenger()
                && !player.hasEffect(MobEffects.LEVITATION);
        if (!airborne) {
            if (self.getSharedFlag(7)) {
                self.setSharedFlag(7, false);
            }
        } else if (!self.getSharedFlag(7) && player.jumping) {
            // 空中按跳跃才展翼 —— 和原版"装备鞘翅后跳一下开始滑翔"一致
            self.setSharedFlag(7, true);
        }
        ci.cancel();
    }
}
