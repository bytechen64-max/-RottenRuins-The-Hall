package org.bytechen.rottenruins.gameplay.magic.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Optional staff reserve; the engine pays player shortfall before mutating the reserve. */
public interface ManaReserve extends CastingStaff {
    boolean usesReserve(IMagicBaseItem spell);
    int mana(ItemStack stack);
    void setMana(ItemStack stack, int amount);
    void onReserveEmpty(ServerPlayer player, InteractionHand hand, ItemStack stack);
}
