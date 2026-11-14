package org.bytechen.rottenruins.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.bytechen.rottenruins.client.combat.slash.trail.anchors.TrackedSlashTrails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class SlashItemCaptureMixin {
    @Inject(method="renderItem",at=@At("HEAD"))
    private void begin(LivingEntity actor,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        TrackedSlashTrails.begin(actor,stack,context);
    }
    @Inject(method="renderItem",at=@At("RETURN"))
    private void end(LivingEntity actor,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        TrackedSlashTrails.finish();
    }
}
