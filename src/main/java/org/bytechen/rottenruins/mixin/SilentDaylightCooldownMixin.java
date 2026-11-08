package org.bytechen.rottenruins.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.bytechen.rottenruins.overworld.registry.items.SilentDaylight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldowns.class)
public abstract class SilentDaylightCooldownMixin {

    @Inject(method = "addCooldown", at = @At("HEAD"), cancellable = true)
    private void rottenruins$skipSilentDaylightCooldown(Item item, int ticks, CallbackInfo ci) {
        if (item instanceof SilentDaylight) {
            ci.cancel();
        }
    }
}
