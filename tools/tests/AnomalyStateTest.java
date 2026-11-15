import java.util.UUID;
import org.bytechen.rottenruins.core.anomaly.AnomalyState;
import org.bytechen.rottenruins.core.anomaly.SkeletonShotCycle;

/** Standalone JDK regression checks; does not launch Minecraft. */
public final class AnomalyStateTest {
    private enum Kind { HEAT, COLD, ACID }

    public static void main(String[] args) {
        AnomalyState<Kind> state = new AnomalyState<>(Kind.class, type -> 10);
        check(!state.add(Kind.HEAT, 3), "partial build-up");
        for (int i = 1; i < 60; i++) state.tick(i);
        check(state.get(Kind.HEAT) == 3, "no decay before three seconds");
        state.tick(60);
        check(state.get(Kind.HEAT) == 2, "first decay at three seconds");
        for (int i = 61; i < 80; i++) state.tick(i);
        check(state.get(Kind.HEAT) == 2, "subsequent decay waits a second");
        state.tick(80);
        check(state.get(Kind.HEAT) == 1, "one point each subsequent second");
        state.add(Kind.HEAT, 1);
        state.set(Kind.COLD, 2);
        for (int i = 81; i < 120; i++) state.tick(i);
        state.add(Kind.HEAT, 1);
        for (int i = 120; i <= 140; i++) state.tick(i);
        check(state.get(Kind.HEAT) == 3, "new hit resets only its type's decay clock");
        check(state.get(Kind.COLD) == 1, "other type keeps decaying");
        int savedProgress = state.get(Kind.COLD);
        int savedIdle = state.idleTicks(Kind.COLD);
        AnomalyState<Kind> restored = new AnomalyState<>(Kind.class, type -> 10);
        restored.restore(Kind.COLD, savedProgress, savedIdle);
        for (int i = 141; i <= 160; i++) restored.tick(i);
        check(restored.get(Kind.COLD) == 0 && !restored.hasAny(), "saved decay resumes to zero");
        check(state.add(Kind.ACID, Integer.MAX_VALUE), "large additions saturate at threshold");
        check(state.get(Kind.ACID) == 10, "saturated progress");
        state.set(Kind.ACID, -5);
        check(state.get(Kind.ACID) == 0, "negative values clear progress");
        check(!state.add(Kind.COLD, 0), "zero addition cannot trigger");

        UUID first = new UUID(1, 1), second = new UUID(2, 2);
        state.recordHit(Kind.HEAT, first, 200, 20);
        check(!state.canHit(Kind.HEAT, first, 219), "continuous attack throttled");
        check(state.canHit(Kind.HEAT, first, 220), "continuous attack resumes at one second");
        check(state.canHit(Kind.HEAT, second, 200), "other attacker independent");
        check(state.canHit(Kind.COLD, first, 200), "other anomaly independent");
        restored.recordHit(Kind.HEAT, first, 200, 0);
        check(!restored.canHit(Kind.HEAT, first, 200), "split damage deduplicated in same tick");
        check(restored.canHit(Kind.HEAT, first, 201), "ordinary hit allowed next tick");
        state.clear();
        check(!state.hasAny() && state.canHit(Kind.HEAT, first, 200), "clear releases progress and cooldowns");

        SkeletonShotCycle shots = new SkeletonShotCycle();
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int i = 0; i < 5; i++) check(!shots.nextIsGhost(), "five normal shots");
            check(shots.nextIsGhost(), "sixth shot is ghost");
        }
        shots.restore(4);
        check(!shots.nextIsGhost() && shots.nextIsGhost(), "saved shot sequence resumes");
        SkeletonShotCycle other = new SkeletonShotCycle();
        check(!other.nextIsGhost(), "skeletons have independent shot sequences");
        shots.restore(Integer.MAX_VALUE);
        check(shots.nextIsGhost(), "invalid saved counter bounded");
        System.out.println("PASS: anomaly decay, save/restore, overflow, per-source throttles and skeleton shot cycles");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
