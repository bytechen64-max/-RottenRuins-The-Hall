package org.bytechen.rottenruins.platform.minecraft.magic;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventorySnapshot;
import org.bytechen.rottenruins.core.magic.state.SpellLoadoutState;
import org.bytechen.rottenruins.core.magic.state.ManaState;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;

import java.util.LinkedHashMap;

/** Current development save format. No legacy keys or migration fallbacks. */
public final class MagicStorage {
    private MagicStorage() {}

    public static CompoundTag writeMana(ManaState.Stored state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mana", state.mana());
        tag.putDouble("restore_fraction", state.restoreFraction());
        return tag;
    }

    public static ManaState.Stored readMana(CompoundTag tag, int initialMana) {
        return new ManaState.Stored(tag.contains("mana") ? tag.getInt("mana") : initialMana,
                tag.getDouble("restore_fraction"));
    }

    public static CompoundTag writePool(SpellInventorySnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        NonNullList<ItemStack> items = NonNullList.withSize(MagicMath.MAX_SLOT_COUNT, ItemStack.EMPTY);
        var savedItems = snapshot.items();
        for (int i = 0; i < Math.min(items.size(), savedItems.size()); i++) items.set(i, savedItems.get(i));
        CompoundTag inventory = new CompoundTag();
        ContainerHelper.saveAllItems(inventory, items);
        tag.put("inventory", inventory);
        CompoundTag cooldowns = new CompoundTag();
        snapshot.cooldowns().forEach(cooldowns::putInt);
        tag.put("cooldowns", cooldowns);
        tag.putInt("selected_slot", snapshot.loadout().selected());
        tag.putIntArray("wheel_slots", snapshot.loadout().wheelSlots());
        return tag;
    }

    public static SpellInventorySnapshot readPool(CompoundTag tag) {
        NonNullList<ItemStack> items = NonNullList.withSize(MagicMath.MAX_SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("inventory"), items);
        var cooldowns = new LinkedHashMap<String, Integer>();
        CompoundTag stored = tag.getCompound("cooldowns");
        for (String key : stored.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            int ticks = stored.getInt(key);
            if (id != null && ticks > 0) cooldowns.put(id.toString(), ticks);
        }
        int[] wheel = tag.getIntArray("wheel_slots");
        return new SpellInventorySnapshot(items, cooldowns, new SpellLoadoutState.Snapshot(tag.getInt("selected_slot"), wheel));
    }
}
