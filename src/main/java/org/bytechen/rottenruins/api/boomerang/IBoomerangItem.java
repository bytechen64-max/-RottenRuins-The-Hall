package org.bytechen.rottenruins.api.boomerang;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.bytechen.rottenruins.overworld.registry.entities.projectile.BoomerangEntity;

/** Server-side behavior hooks. Stack arguments are projectile snapshots, not inventory stacks. */
public interface IBoomerangItem {
    default BoomerangConsumption consumptionMode(ItemStack stack) { return BoomerangConsumption.DURABILITY; }
    default int consumptionCost(ItemStack stack) { return 1; }
    default int cooldownTicks(ItemStack stack) { return 30; }
    default float throwDamage(ItemStack stack) { return 6.0F; }
    default double outboundSpeed(ItemStack stack) { return 0.9D; }
    default double returnSpeed(ItemStack stack) { return 1.2D; }
    default int outboundTicks(ItemStack stack) { return 16; }
    default int maxLifetimeTicks(ItemStack stack) { return 160; }
    /** Null disables the trail. */
    default ParticleOptions trailParticle(ItemStack stack) { return null; }
    default void onThrown(BoomerangEntity projectile, Player player, ItemStack stack) {}
    /** Called once per target per flight leg, including rejected damage (damaged=false). */
    default void onEntityHit(BoomerangEntity projectile, ItemStack stack, EntityHitResult hit, boolean damaged) {}
    default void onBlockHit(BoomerangEntity projectile, ItemStack stack, BlockHitResult hit) {}
    /** Notification only: no inventory item is created on return. */
    default void onReturned(BoomerangEntity projectile, Player player, ItemStack stack) {}
}
