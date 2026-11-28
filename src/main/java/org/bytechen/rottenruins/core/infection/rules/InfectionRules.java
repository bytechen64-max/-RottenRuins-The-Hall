package org.bytechen.rottenruins.core.infection.rules;

import org.bytechen.rottenruins.core.infection.blockspread.BlockSpreadEntry;
import org.bytechen.rottenruins.core.infection.evolution.EvolutionEntry;

/** Publishes both rule tables together, after a complete successful resource reload. */
public final class InfectionRules {
    public record Snapshot(RuleIndex<EvolutionEntry> evolution, RuleIndex<BlockSpreadEntry> spread) {}

    private static volatile Snapshot current = empty();
    private static Runnable reloadAction;

    private InfectionRules() {}

    public static Snapshot current() {
        return current;
    }

    public static void publish(Snapshot snapshot) {
        current = java.util.Objects.requireNonNull(snapshot);
    }

    /** Compatibility with runtime configuration overrides; resource reload replaces them. */
    public static void replaceEvolution(RuleIndex<EvolutionEntry> evolution) {
        current = new Snapshot(evolution, current.spread());
    }

    public static void clear() {
        current = empty();
        reloadAction = null;
    }

    /** Installed by the platform adapter once server resources have loaded. */
    public static void setReloadAction(Runnable action) {
        reloadAction = action;
    }

    public static void reload() {
        if (reloadAction == null) throw new IllegalStateException("No active server resources");
        reloadAction.run();
    }

    private static Snapshot empty() {
        return new Snapshot(new RuleIndex.Builder<EvolutionEntry>().build(),
                new RuleIndex.Builder<BlockSpreadEntry>().build());
    }
}
