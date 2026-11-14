package org.bytechen.rottenruins.core.magic.rules;

/** Shared field timing: visual transitions surround the original 60 damage ticks. */
public final class OlympiadFieldTimeline {
    public static final int OPEN_TICKS = 8;
    public static final int ACTIVE_TICKS = 60;
    public static final int CLOSE_TICKS = 10;
    public static final int TOTAL_TICKS = OPEN_TICKS + ACTIVE_TICKS + CLOSE_TICKS;
    public static final int PULSE_TICKS = 5;

    private OlympiadFieldTimeline() {}

    public static boolean dealsDamage(int age) {
        return age >= OPEN_TICKS && age < OPEN_TICKS + ACTIVE_TICKS;
    }

    public static boolean isPulse(int age) {
        return dealsDamage(age) && (age - OPEN_TICKS) % PULSE_TICKS == 0;
    }

    /** Negative outside the damage stage; otherwise the current outward sweep, 0..1. */
    public static float pulsePhase(float age) {
        if (age < OPEN_TICKS || age >= OPEN_TICKS + ACTIVE_TICKS) return -1.0f;
        return ((age - OPEN_TICKS) % PULSE_TICKS) / PULSE_TICKS;
    }

    /** Closed projectile = 0, fully deployed field = 1. Continuous at both joins. */
    public static float openness(float age) {
        return smooth(age / OPEN_TICKS) * smooth((TOTAL_TICKS - age) / CLOSE_TICKS);
    }

    public static float opacity(float age) {
        return smooth((TOTAL_TICKS - age) / 3.0f);
    }

    private static float smooth(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value));
        return t * t * (3.0f - 2.0f * t);
    }
}
