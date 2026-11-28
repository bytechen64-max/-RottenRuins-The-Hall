package org.bytechen.rottenruins.core.anomaly;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Per-victim progress, decay clocks and short-lived attacker cooldowns; no game references. */
public final class AnomalyState<K extends Enum<K>> {
    public static final int DECAY_DELAY = 60;
    public static final int DECAY_INTERVAL = 20;
    private final K[] types;
    private final ToIntFunction<K> maximum;
    private final Map<K, Integer> progress;
    private final Map<K, Integer> idle;
    private final Map<HitKey<K>, Long> nextHits = new HashMap<>();

    public AnomalyState(Class<K> type, ToIntFunction<K> maximum) {
        this.types = type.getEnumConstants();
        this.maximum = maximum;
        progress = new EnumMap<>(type);
        idle = new EnumMap<>(type);
    }

    public int get(K type) { return progress.getOrDefault(type, 0); }
    public int idleTicks(K type) { return idle.getOrDefault(type, 0); }
    public boolean hasAny() { return !progress.isEmpty(); }

    public void set(K type, int value) {
        restore(type, value, 0);
    }

    public void restore(K type, int value, int idleTicks) {
        int clamped = Math.max(0, Math.min(maximum.applyAsInt(type), value));
        if (clamped == 0) {
            progress.remove(type);
            idle.remove(type);
        } else {
            progress.put(type, clamped);
            idle.put(type, Math.max(0, Math.min(DECAY_DELAY + DECAY_INTERVAL - 1, idleTicks)));
        }
    }

    /** Returns whether the threshold was reached. Addition cannot overflow. */
    public boolean add(K type, int amount) {
        if (amount <= 0) return false;
        set(type, (int) Math.min(maximum.applyAsInt(type), (long) get(type) + amount));
        return get(type) >= maximum.applyAsInt(type);
    }

    public boolean canHit(K type, UUID attacker, long now) {
        return now >= nextHits.getOrDefault(new HitKey<>(type, attacker), Long.MIN_VALUE);
    }

    public void recordHit(K type, UUID attacker, long now, int interval) {
        // Even unthrottled attacks count once per attacker/type/tick (e.g. split damage).
        nextHits.put(new HitKey<>(type, attacker), now + Math.max(1, interval));
    }

    public void tick(long now) {
        nextHits.values().removeIf(next -> next <= now);
        for (K type : types) {
            if (get(type) == 0) continue;
            int elapsed = idleTicks(type) + 1;
            if (elapsed >= DECAY_DELAY && (elapsed - DECAY_DELAY) % DECAY_INTERVAL == 0) {
                int remaining = get(type) - 1;
                restore(type, remaining, DECAY_DELAY);
            } else {
                idle.put(type, elapsed);
            }
        }
    }

    public void clear() {
        progress.clear();
        idle.clear();
        nextHits.clear();
    }

    private record HitKey<K>(K type, UUID attacker) {}
}
