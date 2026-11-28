package org.bytechen.rottenruins.gameplay.infection.difficulty;

import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.api.infection.difficulty.DifficultyType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of known difficulty identifiers for the global difficulty system.
 * <p>
 * Built-in difficulties ({@link DifficultyType Peaceful/Easy/Normal/Hard}) are
 * registered automatically. Downstream mods can optionally register their own
 * identifiers for discovery via the {@code /infcore difficulty list} command
 * and tab-completion.
 * <p>
 * <b>Registration is purely for discoverability.</b> Any valid
 * {@link ResourceLocation} can be used as a difficulty value at runtime
 * regardless of whether it appears in this registry.
 */
public final class DifficultyRegistry {

    private static final Map<ResourceLocation, String> ENTRIES = new LinkedHashMap<>();

    static {
        for (DifficultyType type : DifficultyType.values()) {
            register(type.getId());
        }
    }

    private DifficultyRegistry() {}

    /**
     * Register a difficulty identifier for discovery
     * (e.g. command tab-completion and {@code /infcore difficulty list}).
     *
     * @param id the difficulty ResourceLocation
     */
    public static void register(ResourceLocation id) {
        ENTRIES.putIfAbsent(id, id.toString());
    }

    /**
     * Check whether a difficulty ID has been registered.
     */
    public static boolean isRegistered(ResourceLocation id) {
        return ENTRIES.containsKey(id);
    }

    /**
     * Get an unmodifiable, insertion-ordered view of all registered difficulty IDs.
     */
    public static Set<ResourceLocation> getRegisteredIds() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }
}
