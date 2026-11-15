package org.bytechen.rottenruins.core.anomaly;

/** Five normal shots followed by one ghost shot; owned by each skeleton. */
public final class SkeletonShotCycle {
    public static final int NORMAL_SHOTS = 5;
    private int normalShots;

    public boolean nextIsGhost() {
        if (normalShots == NORMAL_SHOTS) {
            normalShots = 0;
            return true;
        }
        normalShots++;
        return false;
    }

    public int normalShots() { return normalShots; }
    public void restore(int value) { normalShots = Math.max(0, Math.min(NORMAL_SHOTS, value)); }
}
