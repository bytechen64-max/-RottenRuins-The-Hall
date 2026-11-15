package org.bytechen.rottenruins.api.anomaly;

import java.util.Objects;

/** Immutable attack declaration; interval is per attacker, victim and anomaly type. */
public record AnomalyAttack(AnomalyType type, int amount, int intervalTicks) {
    public AnomalyAttack {
        Objects.requireNonNull(type, "type");
        if (amount <= 0 || intervalTicks < 0) throw new IllegalArgumentException("Invalid anomaly attack");
    }
}
