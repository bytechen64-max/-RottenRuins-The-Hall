package org.bytechen.rottenruins.api.infection.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when the global difficulty is about to change or has changed.
 * <p>
 * All sub-events share the same event bus
 * ({@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}).
 * Use {@link #isPre()} / {@link #isPost()} to distinguish.
 *
 * <h3>Pre (cancelable)</h3>
 * Fired before the change. Cancel the event to prevent the difficulty switch.
 *
 * <h3>Post (not cancelable)</h3>
 * Fired after the change has been applied and synced to clients.
 * Use this for difficulty-driven gameplay logic.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onDifficultyChange(DifficultyChangeEvent.Post event) {
 *     if (event.getNewDifficulty().equals(DifficultyType.HARD.getId())) {
 *         // enable hard-mode mechanics
 *     }
 * }
 * }</pre>
 *
 * @see DifficultyType
 */
public abstract class DifficultyChangeEvent extends Event {

    private final ServerLevel overworld;
    private final ResourceLocation oldDifficulty;
    private final ResourceLocation newDifficulty;

    protected DifficultyChangeEvent(ServerLevel overworld, ResourceLocation oldDifficulty,
                                    ResourceLocation newDifficulty) {
        this.overworld = overworld;
        this.oldDifficulty = oldDifficulty;
        this.newDifficulty = newDifficulty;
    }

    /** The overworld dimension. */
    public ServerLevel getOverworld() { return overworld; }

    /** The difficulty before this change. */
    public ResourceLocation getOldDifficulty() { return oldDifficulty; }

    /** The difficulty after this change (proposed value for Pre, confirmed for Post). */
    public ResourceLocation getNewDifficulty() { return newDifficulty; }

    /** @return true if this is a Pre (cancelable) event */
    public boolean isPre() { return this instanceof Pre; }

    /** @return true if this is a Post (notification) event */
    public boolean isPost() { return this instanceof Post; }

    // ==================== Pre ====================

    /**
     * Fired before the global difficulty changes.
     * Cancel this event to prevent the change.
     */
    public static class Pre extends DifficultyChangeEvent {
        public Pre(ServerLevel overworld, ResourceLocation oldDifficulty, ResourceLocation newDifficulty) {
            super(overworld, oldDifficulty, newDifficulty);
        }

        @Override
        public boolean isCancelable() { return true; }
    }

    // ==================== Post ====================

    /**
     * Fired after the global difficulty has been changed and synced to clients.
     * Not cancelable.
     */
    public static class Post extends DifficultyChangeEvent {
        public Post(ServerLevel overworld, ResourceLocation oldDifficulty, ResourceLocation newDifficulty) {
            super(overworld, oldDifficulty, newDifficulty);
        }

        @Override
        public boolean isCancelable() { return false; }
    }
}
