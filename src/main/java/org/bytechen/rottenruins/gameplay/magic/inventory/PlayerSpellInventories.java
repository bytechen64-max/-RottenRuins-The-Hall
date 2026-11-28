package org.bytechen.rottenruins.gameplay.magic.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;

/** Player/world operations around an inventory. Client replicas never return or drop stacks. */
public final class PlayerSpellInventories {
    private PlayerSpellInventories() {}

    public static SpellInventory create(Player owner) {
        return new SpellInventory(() -> MagicStats.slotCount(owner));
    }

    public static void tick(Player owner, SpellInventory inventory) {
        boolean authoritative = !owner.level().isClientSide();
        inventory.tickCooldowns(authoritative);
        if (!authoritative) return;
        for (ItemStack stack : inventory.drainOverflow()) {
            if (!owner.getInventory().add(stack)) owner.drop(stack, false);
        }
    }
}
