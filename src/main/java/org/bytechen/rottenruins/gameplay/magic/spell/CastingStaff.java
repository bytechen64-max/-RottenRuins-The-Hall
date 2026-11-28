package org.bytechen.rottenruins.gameplay.magic.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.core.magic.model.MagicType;

/** Item behavior consumed by the casting engine, independent of a concrete item superclass. */
public interface CastingStaff {
    MagicType getMagicType();
    default float gradeSpellBonus() { return 0f; }
    default boolean accepts(ItemStack staff, IMagicBaseItem spell) { return true; }
    default boolean providesSchoolBonus() { return true; }
    /** Runs after the spell effect at the original wear point, even if the effect returns false. */
    default void onCastComplete(ServerPlayer player, InteractionHand hand, ItemStack staff) {}
}
