package org.bytechen.rottenruins.core.story;

import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Portable trigger declaration. Persistence, JSON and game objects belong outside core. */
public record StoryRule(String id, String event, int priority, int delayTicks, int cooldownTicks,
                        String onceKey, String repeatGroup, boolean avoidRepeat,
                        Conditions conditions, List<Choice> choices) {
    public StoryRule { choices = List.copyOf(choices); }
    public record Choice(String story, int weight) {}
    public record Context(int deaths, int streak, int deathInterval, String cause, String dimension, Set<String> flags) {
        public Context { flags = Set.copyOf(flags); }
    }
    public record Conditions(int minDeaths, int maxDeaths, int minStreak, int maxStreak, int maxDeathInterval,
                             String cause, String dimension, Set<String> requiredFlags, Set<String> forbiddenFlags) {
        public Conditions {
            requiredFlags = Set.copyOf(requiredFlags);
            forbiddenFlags = Set.copyOf(forbiddenFlags);
        }
        public boolean matches(Context context) {
            return context.deaths() >= minDeaths && context.deaths() <= maxDeaths
                    && context.streak() >= minStreak && context.streak() <= maxStreak
                    && context.deathInterval() <= maxDeathInterval
                    && (cause.isEmpty() || cause.equals(context.cause()))
                    && (dimension.isEmpty() || dimension.equals(context.dimension()))
                    && context.flags().containsAll(requiredFlags)
                    && forbiddenFlags.stream().noneMatch(context.flags()::contains);
        }
    }
    public String choose(String previous, IntUnaryOperator random) {
        boolean exclude = avoidRepeat && choices.stream().anyMatch(c -> c.weight() > 0 && !c.story().equals(previous));
        int total = 0;
        for (Choice choice : choices) if (!(exclude && choice.story().equals(previous))) total += choice.weight();
        if (total == 0) return null;
        int roll = random.applyAsInt(total);
        for (Choice choice : choices) {
            if (exclude && choice.story().equals(previous)) continue;
            roll -= choice.weight();
            if (roll < 0) return choice.story();
        }
        throw new IllegalStateException("Invalid weighted story selection");
    }
}
