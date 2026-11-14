import org.bytechen.rottenruins.client.rend.slash.*;

public final class SlashTimelineTest {
    public static void main(String[] args) {
        for (int nativeTicks : new int[] {2, 6, 12, 30}) {
            float cutTime = nativeTicks * SlashMotion.CUT_END;
            near(SlashTimeline.progress(cutTime, nativeTicks), SlashMotion.CUT_END);
            float last = -1;
            for (float tick = -1; tick < cutTime + SlashTimeline.RECOVERY_TICKS + 1; tick += 0.01F) {
                float progress = SlashTimeline.progress(tick, nativeTicks);
                check(progress >= last && progress >= 0 && progress <= 1, "monotonic visual time");
                last = progress;
            }
            // Recovery survives the native swing ending, including attack-speed effects.
            if (nativeTicks < 20) check(SlashTimeline.progress(nativeTicks, nativeTicks) < 1, "no native-end snap");
            float liftTime = cutTime + SlashTimeline.EXIT_TICKS + SlashTimeline.HOLD_TICKS
                    + SlashTimeline.LOWER_TICKS + SlashTimeline.TRANSFER_TICKS;
            near(SlashTimeline.progress(liftTime, nativeTicks), SlashRecovery.LIFT_START);
            check(SlashTimeline.LIFT_TICKS / 20 >= 0.25F, "visible lift takes at least a quarter second");
            check(SlashTimeline.HOLD_TICKS / 20 >= 0.075F, "perceptible offscreen hold");
            near(SlashTimeline.progress(liftTime + SlashTimeline.LIFT_TICKS, nativeTicks), 1);
            float middle = SlashTimeline.progress(liftTime + SlashTimeline.LIFT_TICKS / 2, nativeTicks);
            check(middle > SlashRecovery.LIFT_START && middle < 1, "lift remains animated midway through");
        }
        System.out.println("SlashTimelineTest passed: independent recovery time, native-end continuity, haste/fatigue durations");
    }
    private static void near(float a, float b) { check(Math.abs(a - b) < 0.0002F, a + " != " + b); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
