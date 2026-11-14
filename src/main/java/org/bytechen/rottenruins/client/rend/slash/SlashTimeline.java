package org.bytechen.rottenruins.client.rend.slash;

/** Visual time only: never changes the player's swing duration or attack cooldown. */
public final class SlashTimeline {
    public static final float EXIT_TICKS = 1.8F;
    public static final float HOLD_TICKS = 1.6F;
    public static final float LOWER_TICKS = 1.2F;
    public static final float TRANSFER_TICKS = 1.2F;
    public static final float LIFT_TICKS = 5.2F;
    public static final float RECOVERY_TICKS = EXIT_TICKS + HOLD_TICKS + LOWER_TICKS + TRANSFER_TICKS + LIFT_TICKS;

    private SlashTimeline() {}

    public static float progress(float elapsedTicks, int nativeSwingTicks) {
        float duration = Math.max(1, nativeSwingTicks);
        float cutEnd = duration * SlashMotion.CUT_END;
        if (elapsedTicks <= cutEnd) return Math.max(0, elapsedTicks) / duration;
        return Math.min(1, SlashMotion.CUT_END + (1 - SlashMotion.CUT_END) * (elapsedTicks - cutEnd) / RECOVERY_TICKS);
    }

    public static float recoveryBoundary(float ticks) {
        return SlashMotion.CUT_END + (1 - SlashMotion.CUT_END) * ticks / RECOVERY_TICKS;
    }
}
