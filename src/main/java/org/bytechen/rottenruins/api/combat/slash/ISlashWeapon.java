package org.bytechen.rottenruins.api.combat.slash;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Explicit opt-in for item animation, server AoE combat and client trails. No SwordItem fallback. */
public interface ISlashWeapon {
    SlashSpec slashSpec(Player player, ItemStack stack);
    default SlashPose selectSlashPose(Player player, ItemStack stack, SlashSpec spec, int comboIndex) {
        return spec.poses().get(Math.floorMod(comboIndex, spec.poses().size()));
    }
    /** May supply a different range/sound/trail/timing for each selected pose. */
    default SlashSpec slashSpecForPose(Player player, ItemStack stack, SlashPose pose, SlashSpec base) { return base; }
    default boolean canStartSlash(SlashContext context) { return true; }
    default boolean canHitWithSlash(SlashContext context, LivingEntity target) { return true; }
    /** Return false to skip this target after geometry/LOS/team checks. */
    default boolean beforeSlashHit(SlashContext context, LivingEntity target) { return true; }
    /** Apply the already resolved (including critical) damage once; special weapons may keep their own pipeline. */
    default boolean applySlashDamage(SlashContext context,LivingEntity target,float damage) {
        return target.hurt(context.player().damageSources().playerAttack(context.player()),damage);
    }
    default float slashDamage(SlashContext context, LivingEntity target) {
        return ((float) context.player().getAttributeValue(Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(context.stack(), target.getMobType())) * context.spec().damageMultiplier();
    }
    default boolean allowsSlashCriticals(SlashContext context) { return true; }
    default float slashCriticalMultiplier(SlashContext context, LivingEntity target) { return 1.5F; }
    /** Vanilla multiplies the base damage, then adds the enchantment bonus. Override for custom damage models. */
    default float slashCriticalDamage(SlashContext context, LivingEntity target, float normalDamage, float multiplier) {
        if (normalDamage <= 0) return normalDamage;
        float bonus = Math.min(normalDamage, Math.max(0,
                EnchantmentHelper.getDamageBonus(context.stack(), target.getMobType()) * context.spec().damageMultiplier()));
        return (normalDamage - bonus) * multiplier + bonus;
    }
    default net.minecraft.sounds.SoundEvent slashCriticalSound(SlashContext context) {
        return net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT;
    }
    default void onSlashCritical(SlashContext context, LivingEntity target, float multiplier) {}
    default void onSlashStart(SlashContext context) {}
    default void onSlashPose(SlashContext context) {}
    default void onSlashTick(SlashContext context, int elapsedTicks) {}
    /** Called once per intersecting eligible target, including rejected damage (dealt=false). */
    default void onSlashHit(SlashContext context, LivingEntity target, float requestedDamage, boolean dealt) {}
    default void onSlashMiss(SlashContext context) {}
    default void onSlashEnd(SlashContext context, int successfulHits) {}
    default void onSlashCancelled(SlashContext context) {}
}
