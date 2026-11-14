package org.bytechen.rottenruins.platform.forge.infection.difficulty;

import org.bytechen.rottenruins.utils.logging.InfectionLog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.rottenruins.api.infection.difficulty.DifficultyType;
import org.bytechen.rottenruins.api.infection.event.DifficultyChangeEvent;
import org.bytechen.rottenruins.platform.forge.infection.network.NetworkHelper;
import org.bytechen.rottenruins.platform.forge.infection.network.PacketSyncDifficulty;
import org.jetbrains.annotations.ApiStatus;

/**
 * SavedData holding the single global difficulty for the server.
 * <p>
 * Persisted to disk on the overworld dimension. Synced to all clients on change.
 * Only one instance exists per server (stored on the overworld).
 * <p>
 * <b>Internal class.</b> Downstream mods should use
 * {@link DifficultyHelper#getDifficulty(ServerLevel)} and
 * {@link DifficultyHelper#setDifficulty(ServerLevel, ResourceLocation)} instead
 * of interacting with this class directly.
 */
public class GlobalDifficultyData extends SavedData {

    private static final String KEY_DIFFICULTY = "difficulty";
    private ResourceLocation currentDifficulty;

    public GlobalDifficultyData() {
        this.currentDifficulty = DifficultyType.NORMAL.getId();
    }

    // ==================== Getters / Setters ====================

    /**
     * Get the current global difficulty ResourceLocation.
     */
    public ResourceLocation getCurrentDifficulty() {
        return currentDifficulty;
    }

    /**
     * Attempt to change the global difficulty.
     * <p>
     * Fires {@link DifficultyChangeEvent.Pre} (cancelable), then applies the change,
     * persists to disk, syncs to all clients, then fires {@link DifficultyChangeEvent.Post}.
     *
     * @param overworld     the server overworld (for event context)
     * @param newDifficulty the new difficulty identifier
     * @return true if the change was applied, false if canceled or already active
     */
    public boolean setDifficulty(ServerLevel overworld, ResourceLocation newDifficulty) {
        if (newDifficulty == null || newDifficulty.equals(currentDifficulty)) {
            return false;
        }

        // Pre event (cancelable)
        DifficultyChangeEvent.Pre preEvent =
                new DifficultyChangeEvent.Pre(overworld, currentDifficulty, newDifficulty);
        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            InfectionLog.LOGGER.debug("Difficulty change from {} to {} was canceled by event listener.",
                    currentDifficulty, newDifficulty);
            return false;
        }

        ResourceLocation old = this.currentDifficulty;
        this.currentDifficulty = newDifficulty;
        setDirty();  // SavedData persistence

        // Sync to all clients
        NetworkHelper.sendToAllClients(new PacketSyncDifficulty(newDifficulty));

        InfectionLog.LOGGER.info("Global difficulty changed: {} -> {}", old, newDifficulty);

        // Post event (notification)
        MinecraftForge.EVENT_BUS.post(
                new DifficultyChangeEvent.Post(overworld, old, newDifficulty));
        return true;
    }

    // ==================== SavedData persistence ====================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString(KEY_DIFFICULTY, currentDifficulty.toString());
        return tag;
    }

    /**
     * Load from disk factory. Called by {@code DimensionDataStorage.computeIfAbsent}.
     */
    @ApiStatus.Internal
    public static GlobalDifficultyData load(CompoundTag tag) {
        GlobalDifficultyData data = new GlobalDifficultyData();
        String raw = tag.getString(KEY_DIFFICULTY);
        if (!raw.isEmpty()) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed != null) {
                data.currentDifficulty = parsed;
            }
        }
        return data;
    }
}
