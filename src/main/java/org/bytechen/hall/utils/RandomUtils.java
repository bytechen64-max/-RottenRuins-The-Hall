package org.bytechen.hall.utils;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtils
{
    public static <T> T randomOf(T a, T b) {
        return ThreadLocalRandom.current().nextBoolean() ? a : b;
    }

    public static <T> T randomOf(T a, T b, T c) {
        int idx = ThreadLocalRandom.current().nextInt(3);
        return idx == 0 ? a : (idx == 1 ? b : c);
    }

    public static <T> T randomOf(T a, T b, T c, T d) {
        int idx = ThreadLocalRandom.current().nextInt(4);
        return switch (idx) {
            case 0 -> a;
            case 1 -> b;
            case 2 -> c;
            default -> d;
        };
    }

    public static <T> T randomOf(T a, T b, T c, T d, T e) {
        int idx = ThreadLocalRandom.current().nextInt(5);
        return switch (idx) {
            case 0 -> a;
            case 1 -> b;
            case 2 -> c;
            case 3 -> d;
            default -> e;
        };
    }

    @SafeVarargs
    public static <T> T randomOf(T... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("At least one argument is required");
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
