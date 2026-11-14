import org.bytechen.rottenruins.core.magic.rules.OlympiadFieldTimeline;

public final class OlympiadFieldTimelineTest {
    public static void main(String[] args) {
        int hits = 0;
        for (int age = 1; age <= OlympiadFieldTimeline.TOTAL_TICKS; age++) {
            if (OlympiadFieldTimeline.dealsDamage(age)) {
                hits++;
                check(OlympiadFieldTimeline.openness(age) == 1.0f,
                        "Damage must only occur at full visual radius");
            }
        }
        check(hits == 60, "Keep exactly 60 damage ticks");
        int beats = 0;
        for (int age = 0; age <= OlympiadFieldTimeline.TOTAL_TICKS; age++) {
            if (OlympiadFieldTimeline.isPulse(age)) {
                beats++;
                check(OlympiadFieldTimeline.dealsDamage(age), "Beats belong to active damage only");
                check(OlympiadFieldTimeline.pulsePhase(age) == 0, "Visual sweep starts on audio beat");
            }
        }
        check(beats == 12, "Twelve feedback beats for sixty damage ticks");
        check(OlympiadFieldTimeline.pulsePhase(7.9f) < 0, "No early sweep");
        check(OlympiadFieldTimeline.pulsePhase(68) < 0, "No sweep during collapse");
        check(!OlympiadFieldTimeline.dealsDamage(7), "Opening must not deal damage");
        check(!OlympiadFieldTimeline.dealsDamage(68), "Closing must not deal damage");
        check(OlympiadFieldTimeline.openness(0) == 0.0f, "Start at projectile size");
        check(OlympiadFieldTimeline.openness(78) == 0.0f, "Finish fully collapsed");
        check(OlympiadFieldTimeline.opacity(78) == 0.0f, "Disappear before removal");
        check(OlympiadFieldTimeline.openness(-1) == 0.0f, "Clamp early samples");
        check(OlympiadFieldTimeline.openness(100) == 0.0f, "Clamp delayed removal");

        float previous = 0.0f;
        for (float age = 0; age <= 8; age += 0.125f) {
            float value = OlympiadFieldTimeline.openness(age);
            check(value >= previous, "Opening must grow monotonically between ticks");
            previous = value;
        }
        previous = 1.0f;
        for (float age = 68; age <= 78; age += 0.125f) {
            float value = OlympiadFieldTimeline.openness(age);
            check(value <= previous, "Closing must collapse monotonically between ticks");
            check(value >= 0 && value <= 1, "Keep radius within bounds");
            previous = value;
        }
        System.out.println("OlympiadFieldTimelineTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
