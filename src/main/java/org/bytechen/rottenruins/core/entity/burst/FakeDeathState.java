package org.bytechen.rottenruins.core.entity.burst;

import java.util.function.IntSupplier;

/** Per-entity countdown and client clock, independent of Minecraft, Forge and animation libraries. */
public final class FakeDeathState {
    private int remainingTicks;
    private long clientStartTime = -1;

    /** Preserve the old probability convention: zero or negative means unconditional. */
    public static boolean canStart(boolean enabled, boolean alreadyDying, int chance, IntSupplier roll) {
        if (!enabled || alreadyDying) return false;
        return chance <= 0 || roll.getAsInt() < chance;
    }

    public void start(int duration) {
        remainingTicks = duration;
    }

    /** True only on the positive-countdown-to-zero transition. The entity executes the end effects. */
    public boolean tick() {
        if (remainingTicks <= 0) return false;
        return --remainingTicks == 0;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    /** Raw restore preserves existing save behavior, including nonpositive legacy timer values. */
    public void restoreRemainingTicks(int ticks) {
        remainingTicks = ticks;
    }

    public void onClientStateChanged(boolean dying, long gameTime) {
        clientStartTime = dying ? gameTime : -1;
    }

    public float progress(boolean clientSide, long gameTime, int deathDuration,
                          int scaleDuration, float partialTick) {
        if (scaleDuration <= 0) return 1.0f;
        float elapsed;
        if (clientSide) {
            if (clientStartTime < 0) return 0.0f;
            elapsed = (gameTime - clientStartTime) + partialTick;
        } else {
            elapsed = (deathDuration - remainingTicks) + partialTick;
        }
        return BurstMath.progress(elapsed, scaleDuration);
    }
}
