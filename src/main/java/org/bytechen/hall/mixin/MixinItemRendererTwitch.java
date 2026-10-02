package org.bytechen.hall.mixin;

import org.bytechen.hall.client.rend.twitch.ItemTwitchHelper;
import org.bytechen.hall.client.rend.twitch.ITwitchItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the glitch twitch {@link PoseStack} transforms at the HEAD of
 * {@code ItemRenderer.render()} so every render pass (base texture, cosmic
 * overlay, glint, outline) is affected uniformly.
 *
 * <h3>Injection point</h3>
 * HEAD — before Forge camera transforms and before any quads are drawn.
 * The twitch translation/rotation/scale is composed on top of the caller's
 * existing PoseStack, so it naturally works in every context (GUI, hand,
 * ground, item frame).
 *
 * <h3>Eligibility</h3>
 * <ul>
 *   <li>{@link org.bytechen.hall.client.cosmic.BakedModelCosmic} — cosmic shader items always twitch</li>
 *   <li>{@link ITwitchItem} — opt-in interface for non-cosmic items</li>
 *   <li>No effect on other items — zero overhead</li>
 * </ul>
 *
 * @see ItemTwitchHelper
 * @see ITwitchItem
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererTwitch {

    @Inject(method = "render", at = @At("HEAD"))
    private void twitch$applyGlitchTransforms(ItemStack stack, ItemDisplayContext context,
                                              boolean leftHand, PoseStack poseStack,
                                              MultiBufferSource buffer, int light, int overlay,
                                              BakedModel model, CallbackInfo ci) {
        // Never twitch in GUI — would be distracting during inventory management
        if (context == ItemDisplayContext.GUI) return;

        // Check interface-level opt-out
        if (stack.getItem() instanceof ITwitchItem ti && !ti.twitchShouldRender(context)) return;

        if (!ItemTwitchHelper.shouldTwitch(stack, model)) return;

        // Per-model opt-out ("cosmic": { "twitch": false }).
        // needed in addition to shouldTwitch: in the deferred path the renderer
        // may be handed a wrapper model rather than the BakedModelCosmic itself.
        if (!ItemTwitchHelper.twitchEnabledFor(stack, model)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ItemTwitchHelper.applyTwitch(poseStack, stack, mc.level.getGameTime());
    }
}
