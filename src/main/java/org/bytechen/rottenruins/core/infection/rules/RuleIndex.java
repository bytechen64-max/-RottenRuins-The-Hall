package org.bytechen.rottenruins.core.infection.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable lookup by normalized type and source IDs. No Minecraft or loader dependency. */
public final class RuleIndex<E> {
    private final Map<String, Map<String, E>> byType;

    private RuleIndex(Map<String, Map<String, E>> entries) {
        Map<String, Map<String, E>> copy = new LinkedHashMap<>();
        entries.forEach((type, sources) -> copy.put(type,
                Collections.unmodifiableMap(new LinkedHashMap<>(sources))));
        byType = Collections.unmodifiableMap(copy);
    }

    public E get(String type, String source) {
        return byType.getOrDefault(type, Map.of()).get(source);
    }

    public boolean containsSource(String source) {
        return byType.values().stream().anyMatch(entries -> entries.containsKey(source));
    }

    public int size() {
        return byType.values().stream().mapToInt(Map::size).sum();
    }

    public Builder<E> toBuilder() {
        Builder<E> builder = new Builder<>();
        byType.forEach((type, entries) -> entries.forEach((source, entry) -> builder.put(type, source, entry)));
        return builder;
    }

    public static final class Builder<E> {
        private final Map<String, Map<String, E>> entries = new LinkedHashMap<>();

        /** Later entries for the same type/source replace the entire earlier entry. */
        public void put(String type, String source, E entry) {
            entries.computeIfAbsent(type, key -> new LinkedHashMap<>()).put(source, entry);
        }

        public RuleIndex<E> build() {
            return new RuleIndex<>(entries);
        }
    }
}
