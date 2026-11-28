package org.bytechen.rottenruins.api.infection.difficulty;

import org.bytechen.rottenruins.core.infection.InfectionIds;

import net.minecraft.resources.ResourceLocation;

/**
 * Built-in global difficulty type references.
 * <p>
 * These are convenience constants mapping to {@link ResourceLocation} identifiers.
 * Downstream mods query the current difficulty by ResourceLocation and compare
 * against these constants or their own custom identifiers.
 * <p>
 * <b>This enum provides NO gameplay logic</b> — it only defines the identifiers
 * and a numeric ordering hint. Mods implement their own difficulty behavior by
 * listening to {@link org.bytechen.rottenruins.api.infection.event.DifficultyChangeEvent}.
 * <p>
 * Usage:
 * <pre>{@code
 * ResourceLocation current = DifficultyHelper.getDifficulty(level);
 * if (current.equals(DifficultyType.HARD.getId())) {
 *     // apply hard-mode logic
 * }
 * }</pre>
 */
public enum DifficultyType {

    PEACEFUL("peaceful", 0),
    EASY("easy", 1),
    NORMAL("normal", 2),
    HARD("hard", 3);

    private final ResourceLocation id;
    private final int defaultOrder;

    DifficultyType(String name, int defaultOrder) {
        this.id = ResourceLocation.fromNamespaceAndPath(InfectionIds.NAMESPACE, name);
        this.defaultOrder = defaultOrder;
    }

    /**
     * The fully-qualified ResourceLocation for this difficulty.
     * e.g. {@code infcore:normal}
     */
    public ResourceLocation getId() {
        return id;
    }

    /**
     * A numeric hint for ordering/ranking.
     * Higher values indicate higher difficulty.
     */
    public int getDefaultOrder() {
        return defaultOrder;
    }
}
