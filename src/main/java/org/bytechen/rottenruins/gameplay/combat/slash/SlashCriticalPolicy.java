package org.bytechen.rottenruins.gameplay.combat.slash;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
/** Gameplay eligibility policy, separate from loader critical events. */
public final class SlashCriticalPolicy {
    private SlashCriticalPolicy() {}
    public static boolean eligible(Player player) {
        return !player.onGround() && player.getDeltaMovement().y <= 0.08
                && !player.onClimbable() && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying();
    }
}
