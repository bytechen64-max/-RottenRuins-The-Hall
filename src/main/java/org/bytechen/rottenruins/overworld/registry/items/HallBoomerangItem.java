package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

/** Hall boomerang stays in hand, using one durability and a 30-tick cooldown per throw. */
public final class HallBoomerangItem extends BoomerangItem {
    public HallBoomerangItem() { super(new Properties().durability(384)); }

    @Override
    public ParticleOptions trailParticle(ItemStack stack) { return RegisterParticles.HALL_DUST.get(); }
}
