package org.bytechen.rottenruins.mixin;

import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.overworld.registry.RegisterEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerNaturalHealingMixin {
    // Peaceful difficulty has an additional natural healing path outside FoodData.
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void rottenRuins$naturalHealing(Player player, float amount) {
        if (!player.hasEffect(RegisterEffect.APOPTOSIS.get())) player.heal(amount);
    }
}
