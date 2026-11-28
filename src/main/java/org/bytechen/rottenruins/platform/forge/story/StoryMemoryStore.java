package org.bytechen.rottenruins.platform.forge.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.bytechen.rottenruins.core.story.StoryMemory;
import java.util.*;

/** Owns Forge persistent player data and the current story save keys. */
public final class StoryMemoryStore {
    private static final String STATE_KEY = "rotten_ruins:story_state";
    private StoryMemoryStore() {}
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
    public static StoryMemory load(Player player) {
        CompoundTag root = persisted(player);
        if (!root.contains(STATE_KEY)) root.put(STATE_KEY, new CompoundTag());
        CompoundTag state = root.getCompound(STATE_KEY);
        Map<String, Boolean> flags = new HashMap<>(), once = new HashMap<>();
        Map<String, String> previous = new HashMap<>(); Map<String, Long> cooldowns = new HashMap<>();
        CompoundTag f = state.getCompound("flags"), p = state.getCompound("previous"), c = state.getCompound("cooldowns");
        for (String key : f.getAllKeys()) flags.put(key, f.getBoolean(key));
        for (String key : p.getAllKeys()) previous.put(key, p.getString(key));
        for (String key : c.getAllKeys()) cooldowns.put(key, c.getLong(key));
        for (String key : root.getAllKeys()) once.put(key, root.getBoolean(key));
        return new StoryMemory(flags, previous, cooldowns, once, state.getCompound("once_keys").getAllKeys());
    }
    public static void save(Player player, StoryMemory memory) {
        CompoundTag root = persisted(player), state = root.getCompound(STATE_KEY);
        CompoundTag flags = new CompoundTag(), previous = new CompoundTag(), cooldowns = new CompoundTag(), keys = new CompoundTag();
        memory.flags().forEach(flags::putBoolean); memory.previous().forEach(previous::putString);
        memory.cooldowns().forEach(cooldowns::putLong);
        for (String key : memory.recordedOnceKeys()) { keys.putBoolean(key, true); root.putBoolean(key, memory.consumed(key)); }
        state.put("flags", flags); state.put("previous", previous); state.put("cooldowns", cooldowns); state.put("once_keys", keys);
        root.put(STATE_KEY, state);
    }
    public static void copy(Player original, Player replacement, Set<String> currentOnceKeys) {
        CompoundTag from = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag to = replacement.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (from.contains(STATE_KEY)) to.put(STATE_KEY, from.getCompound(STATE_KEY).copy());
        for (String key : from.getCompound(STATE_KEY).getCompound("once_keys").getAllKeys())
            if (from.contains(key)) to.putBoolean(key, from.getBoolean(key));
        if (from.contains("rotten_ruins:opening_story_seen")) to.putBoolean("rotten_ruins:opening_story_seen", from.getBoolean("rotten_ruins:opening_story_seen"));
        for (String onceKey : currentOnceKeys) {
            if (!onceKey.isEmpty() && from.contains(onceKey)) to.putBoolean(onceKey, from.getBoolean(onceKey));
        }
        replacement.getPersistentData().put(Player.PERSISTED_NBT_TAG, to);
    }
}
