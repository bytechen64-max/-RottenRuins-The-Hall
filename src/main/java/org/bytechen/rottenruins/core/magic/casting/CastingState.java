package org.bytechen.rottenruins.core.magic.casting;

import org.bytechen.rottenruins.core.magic.rules.MagicMath;

/** Cast phase and clocks. No player, item, target, payment, or effect execution. */
public final class CastingState {
    public enum Phase { IDLE, CHARGING, CHANNELING }
    public enum Source { RIGHT_CLICK, WHEEL }
    public record ChargeDecision(int heldTicks, float ratio, boolean allowed) {}

    private static final int KEEP_BILLING_INTERVAL = 20;
    private Phase phase = Phase.IDLE;
    private Source source = Source.RIGHT_CLICK;
    private int heldTicks;
    private int billingTicks;
    private int pulseTicks;
    private boolean firstPulse = true;

    public Phase phase() { return phase; }
    public Source source() { return source; }
    public int heldTicks() { return heldTicks; }
    public boolean isIdle() { return phase == Phase.IDLE; }
    public boolean isCharging() { return phase == Phase.CHARGING; }
    public boolean isChanneling() { return phase == Phase.CHANNELING; }

    public void beginCharging(Source source) { begin(Phase.CHARGING, source); }
    public void beginChanneling(Source source) { begin(Phase.CHANNELING, source); }

    private void begin(Phase phase, Source source) {
        reset();
        this.phase = phase;
        this.source = java.util.Objects.requireNonNull(source);
    }

    public void updateHeldTicks(int ticks) { heldTicks = Math.max(0, ticks); }

    /** Vanilla supplies elapsed use time; wheel sessions advance their own server clock. */
    public int nextWheelTick() {
        return heldTicks == Integer.MAX_VALUE ? heldTicks : heldTicks + 1;
    }

    public boolean tickBilling() {
        if (!isChanneling()) return false;
        if (++billingTicks < KEEP_BILLING_INTERVAL) return false;
        billingTicks = 0;
        return true;
    }

    /** First channel tick fires immediately, then ticks 1+N, 1+2N, ... */
    public boolean tickPulse(int interval) {
        if (!isChanneling()) return false;
        if (interval <= 1) {
            pulseTicks = 0;
            firstPulse = false;
            return true;
        }
        if (firstPulse) {
            firstPulse = false;
            pulseTicks = 0;
            return true;
        }
        if (++pulseTicks >= interval) {
            pulseTicks = 0;
            return true;
        }
        return false;
    }

    public int wheelReleaseTicks(int requiredTicks, int toleranceTicks) {
        if (requiredTicks <= 0) return heldTicks;
        return (int) Math.min(requiredTicks, (long) heldTicks + Math.max(0, toleranceTicks));
    }

    public static ChargeDecision evaluateCharge(int heldTicks, int requiredTicks, float minimumRatio) {
        float ratio = MagicMath.chargeRatio(heldTicks, requiredTicks);
        return new ChargeDecision(heldTicks, ratio, ratio > 0f && ratio >= minimumRatio);
    }

    public void reset() {
        phase = Phase.IDLE;
        source = Source.RIGHT_CLICK;
        heldTicks = billingTicks = pulseTicks = 0;
        firstPulse = true;
    }
}
