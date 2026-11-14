package org.bytechen.rottenruins.gameplay.magic.inventory;

import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.core.magic.state.SpellLoadoutState;

import java.util.List;
import java.util.Map;

/** Detached game-data snapshot; neither NBT nor a Forge capability. */
public record SpellInventorySnapshot(List<ItemStack> items, Map<String, Integer> cooldowns,
                                SpellLoadoutState.Snapshot loadout) {
    public SpellInventorySnapshot {
        items = items.stream().map(ItemStack::copy).toList();
        cooldowns = Map.copyOf(cooldowns);
        loadout = java.util.Objects.requireNonNull(loadout);
    }

    @Override public List<ItemStack> items() { return items.stream().map(ItemStack::copy).toList(); }
}
