package org.bytechen.rottenruins.utils.health.runtime;

public final class RevertWatch {

    public static final int STABLE_TICKS_REQUIRED = 3;

    private static final float EPS = 0.005f;

    private RevertWatch() {}

    public static final class State {
        public final float target;
        public final float revertValue;
        public int attemptsLeft;
        public int holdTicksLeft;
        public int stableTicks;
        public boolean reportedPullback;

        public State(float target, float revertValue, int attemptsLeft, int holdTicks) {
            this.target = target;
            this.revertValue = revertValue;
            this.attemptsLeft = Math.max(0, attemptsLeft);
            this.holdTicksLeft = Math.max(0, holdTicks);
        }
    }

    public enum Action {

        DONE,

        STABLE_WAIT,

        REWRITE,

        HOLD_EXPIRED,

        GIVE_UP,

        FORGET
    }

    public record Decision(Action action, String note) {}

    public static Decision step(State s, float now) {
        if (Float.isNaN(now)) return new Decision(Action.FORGET, null);

        if (Math.abs(now - s.target) < EPS) {
            if (++s.stableTicks >= STABLE_TICKS_REQUIRED) return new Decision(Action.DONE, null);
            return new Decision(Action.STABLE_WAIT, null);
        }
        s.stableTicks = 0;

        boolean backToBefore = Math.abs(now - s.revertValue) < EPS;
        boolean pulledBack = !backToBefore
                && Math.abs(now - s.revertValue) < Math.abs(s.target - s.revertValue);

        String note = null;
        if (pulledBack && !s.reportedPullback) {
            s.reportedPullback = true;
            note = "读数被顶回「第三个值」" + now + "（目标 " + s.target + "，改血前 " + s.revertValue + "）";
        }

        if (!backToBefore && !pulledBack) return new Decision(Action.FORGET, note);

        if (s.attemptsLeft > 0) {
            s.attemptsLeft--;
            return new Decision(Action.REWRITE, note);
        }

        if (s.holdTicksLeft > 0) {
            s.holdTicksLeft--;
            return new Decision(s.holdTicksLeft == 0 ? Action.HOLD_EXPIRED : Action.REWRITE, note);
        }

        return new Decision(Action.GIVE_UP, note);
    }
}
