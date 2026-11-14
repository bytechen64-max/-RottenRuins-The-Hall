package org.bytechen.rottenruins.core.story;

import java.util.*;

/** Detached player story memory. Persistence and transport types do not enter the core. */
public final class StoryMemory {
    private final Map<String, Boolean> flags;
    private final Map<String, String> previous;
    private final Map<String, Long> cooldowns;
    private final Map<String, Boolean> once;
    private final Set<String> recordedOnceKeys;
    public StoryMemory(Map<String, Boolean> flags, Map<String, String> previous,
                       Map<String, Long> cooldowns, Map<String, Boolean> once, Set<String> recordedOnceKeys) {
        this.flags = new HashMap<>(flags); this.previous = new HashMap<>(previous);
        this.cooldowns = new HashMap<>(cooldowns); this.once = new HashMap<>(once);
        this.recordedOnceKeys = new HashSet<>(recordedOnceKeys);
    }
    public Map<String, Boolean> flags() { return Map.copyOf(flags); }
    public Map<String, String> previous() { return Map.copyOf(previous); }
    public Map<String, Long> cooldowns() { return Map.copyOf(cooldowns); }
    public Map<String, Boolean> once() { return Map.copyOf(once); }
    public Set<String> recordedOnceKeys() { return Set.copyOf(recordedOnceKeys); }
    public boolean consumed(String key) { return once.getOrDefault(key, false); }
    public String previous(String group) { return previous.getOrDefault(group, ""); }
    public Long lastPlayed(String rule) { return cooldowns.get(rule); }
    public void flag(String key, boolean enabled) { flags.put(key, enabled); }
    public void commit(String rule, String group, String story, String onceKey, long tick) {
        previous.put(group, story); cooldowns.put(rule, tick);
        if (!onceKey.isEmpty()) { once.put(onceKey, true); recordedOnceKeys.add(onceKey); }
    }
}
