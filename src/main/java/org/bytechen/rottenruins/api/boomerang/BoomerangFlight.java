package org.bytechen.rottenruins.api.boomerang;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/** Behavior-facing flight handle; extensions do not depend on a registered implementation class. */
public interface BoomerangFlight {
    Projectile projectile();
    ItemStack getItem();
    boolean isReturning();
    void beginReturn();
}
