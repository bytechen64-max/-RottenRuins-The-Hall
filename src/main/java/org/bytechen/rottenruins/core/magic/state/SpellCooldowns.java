package org.bytechen.rottenruins.core.magic.state;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cooldowns keyed by spell identity, shared by every stack/slot using that identity. */
public final class SpellCooldowns {
    private final Map<String, Integer> remaining = new LinkedHashMap<>();

    public int remaining(String id) { return id == null ? 0 : remaining.getOrDefault(id, 0); }

    /** Starting a shorter cooldown never shortens one already running. */
    public void start(String id, int ticks) {
        if (id == null || id.isBlank() || ticks <= 0) return;
        remaining.merge(id, ticks, Math::max);
    }

    /** Returns whether any cooldown expired. Ordinary countdown progress does not request sync. */
    public boolean tick() {
        boolean expired = false;
        Iterator<Map.Entry<String, Integer>> iterator = remaining.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                iterator.remove();
                expired = true;
            } else entry.setValue(ticks);
        }
        return expired;
    }

    public Map<String, Integer> snapshot() { return Map.copyOf(remaining); }

    public void load(Map<String, Integer> snapshot) {
        remaining.clear();
        snapshot.forEach((id, ticks) -> {
            if (ticks != null) start(id, ticks);
        });
    }
}
