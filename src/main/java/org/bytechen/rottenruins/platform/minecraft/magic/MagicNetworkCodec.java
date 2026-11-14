package org.bytechen.rottenruins.platform.minecraft.magic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.gameplay.magic.inventory.SpellInventorySnapshot;
import org.bytechen.rottenruins.core.magic.state.SpellLoadoutState;
import org.bytechen.rottenruins.core.magic.rules.MagicMath;
import org.bytechen.rottenruins.core.magic.state.ManaState;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Wire encoding is deliberately separate from the persistent NBT format. */
public final class MagicNetworkCodec {
    private static final int MAX_COOLDOWNS = 16_384;
    private static final int MAX_WHEEL_SLOTS = SpellLoadoutState.TOTAL_WHEEL_SLOTS;

    private MagicNetworkCodec() {}

    public static void writeMana(FriendlyByteBuf buf, ManaState.View state) {
        buf.writeVarInt(state.mana());
        buf.writeVarInt(state.maximum());
    }

    public static ManaState.View readMana(FriendlyByteBuf buf) {
        return new ManaState.View(buf.readVarInt(), buf.readVarInt());
    }

    public static void writePool(FriendlyByteBuf buf, SpellInventorySnapshot state) {
        var items = state.items();
        requireCount(items.size(), MagicMath.MAX_SLOT_COUNT);
        buf.writeVarInt(items.size());
        for (ItemStack item : items) buf.writeItem(item);
        requireCount(state.cooldowns().size(), MAX_COOLDOWNS);
        buf.writeVarInt(state.cooldowns().size());
        state.cooldowns().forEach((id, ticks) -> {
            buf.writeUtf(id);
            buf.writeVarInt(ticks);
        });
        buf.writeVarInt(state.loadout().selected());
        int[] wheel = state.loadout().wheelSlots();
        requireCount(wheel.length, MAX_WHEEL_SLOTS);
        buf.writeVarInt(wheel.length);
        for (int slot : wheel) buf.writeVarInt(slot);
    }

    public static SpellInventorySnapshot readPool(FriendlyByteBuf buf) {
        var items = new ArrayList<ItemStack>();
        int count = requireCount(buf.readVarInt(), MagicMath.MAX_SLOT_COUNT);
        for (int i = 0; i < count; i++) items.add(buf.readItem());
        var cooldowns = new LinkedHashMap<String, Integer>();
        int cooldownCount = requireCount(buf.readVarInt(), MAX_COOLDOWNS);
        for (int i = 0; i < cooldownCount; i++) {
            ResourceLocation id = ResourceLocation.tryParse(buf.readUtf());
            int ticks = buf.readVarInt();
            if (id != null && ticks > 0) cooldowns.put(id.toString(), ticks);
        }
        int selected = buf.readVarInt();
        int[] wheel = new int[requireCount(buf.readVarInt(), MAX_WHEEL_SLOTS)];
        for (int i = 0; i < wheel.length; i++) wheel[i] = buf.readVarInt();
        return new SpellInventorySnapshot(items, cooldowns, new SpellLoadoutState.Snapshot(selected, wheel));
    }

    private static int requireCount(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid magic snapshot size: " + count);
        return count;
    }
}
