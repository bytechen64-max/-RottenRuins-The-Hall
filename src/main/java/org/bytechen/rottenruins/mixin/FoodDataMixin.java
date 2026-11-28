package org.bytechen.rottenruins.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    // Skip both regeneration branches, including their regeneration exhaustion cost.
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean rottenRuins$canRegenerate(Player player) {
        return !player.hasEffect(RegisterEffect.APOPTOSIS.get()) && player.isHurt();
    }
}
