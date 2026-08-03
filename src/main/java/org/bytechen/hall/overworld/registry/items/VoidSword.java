package org.bytechen.hall.overworld.registry.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;


/**
 * VoidSword — cosmic starfield + purple-gold gradient outline + rainbow glint.
 *
 * <h3>Visual layers</h3>
 * <ol>
 *   <li>Base item texture (standard minecraft render)</li>
 *   <li>Cosmic starfield shader overlay (via {@code "splendiding:cosmic"} model loader)</li>
 *   <li>Purple→gold flowing gradient outline (registered in {@code GlintRenderManager})</li>
 *   <li>Rainbow shimmer glint (registered in {@code GlintRenderManager})</li>
 * </ol>
 */
public class VoidSword extends SwordItem {

    public VoidSword() {
        super(Tiers.NETHERITE, 8, -2.4f, new Item.Properties().fireResistant());
    }

    @Override public Component getName(ItemStack stack) { return Component.translatable(this.getDescriptionId(stack)); }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        ItemStack r = itemStack.copy(); r.setCount(1); return r;
    }

    @Override
    public boolean hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity attacker) {

        target.hurt(attacker.damageSources().mobAttack(attacker), 500);
        SwordAuraEntity.spawn(attacker.level(), target.getBoundingBox().getCenter(),
                1.5f, 40, 2.0f);
        return super.hurtEnemy(itemStack, target, attacker);
    }
}
