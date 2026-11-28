package org.bytechen.rottenruins.core.infection.rules;

import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/** Shared integer-weight selection. The caller supplies randomness, including its exact consumption. */
public final class WeightedSelection {
    private WeightedSelection() {}

    public static <T> T pick(List<T> values, ToIntFunction<T> weight, IntUnaryOperator nextInt) {
        long total = 0;
        for (T value : values) {
            int current = weight.applyAsInt(value);
            if (current < 0) throw new IllegalArgumentException("Negative selection weight");
            total += current;
        }
        if (total == 0) return null;
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Selection weight total overflows");
        int roll = nextInt.applyAsInt((int) total);
        if (roll < 0 || roll >= total) throw new IllegalArgumentException("Random result outside selection range");
        int cumulative = 0;
        for (T value : values) {
            cumulative += weight.applyAsInt(value);
            if (roll < cumulative) return value;
        }
        return values.get(values.size() - 1);
    }
}
