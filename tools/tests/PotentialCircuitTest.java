import org.bytechen.rottenruins.overworld.energy.PotentialCircuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 独立运行：javac -d build/circuit-test <PotentialCircuit.java> tools/tests/PotentialCircuitTest.java */
public class PotentialCircuitTest {
    public static void main(String[] args) {
        Map<Long, List<Long>> line = new HashMap<>();
        for (long i = 0; i < 80; i++) {
            List<Long> neighbors = new ArrayList<>();
            if (i > 0) neighbors.add(i - 1);
            if (i < 79) neighbors.add(i + 1);
            line.put(i, neighbors);
        }
        check(PotentialCircuit.solve(line, List.of(0L), false).isEmpty(), "high without low must be off");
        check(PotentialCircuit.solve(line, List.of(), true).isEmpty(), "low without high must be off");
        var powered = PotentialCircuit.solve(line, List.of(0L), true);
        check(powered.get(0L) == 15 && powered.get(3L) == 15, "first four wires remain 15");
        check(powered.get(4L) == 14 && powered.get(8L) == 13, "one power level per four path steps");
        check(powered.get(56L) == 1 && powered.get(79L) == 1, "long connection stays active at minimum 1");
        var bothEnds = PotentialCircuit.solve(line, List.of(0L, 79L), true);
        check(bothEnds.get(79L) == 15 && bothEnds.get(75L) == 14, "nearest of multiple high contacts wins");
        line.get(0L).add(79L);
        line.get(79L).add(0L);
        var ring = PotentialCircuit.solve(line, List.of(0L), true);
        check(ring.size() == 80 && ring.get(76L) == 14, "cycles terminate and shortest path wins");
        check(PotentialCircuit.solve(Map.of(), List.of(0L), true).isEmpty(), "removed source cannot power empty graph");
        var forward=Map.of(0L,List.of(1L),1L,List.of(2L),2L,List.<Long>of());
        check(PotentialCircuit.solve(forward,List.of(0L),List.of(2L)).size()==3,"directed machine chain must conduct forward");
        check(PotentialCircuit.solve(forward,List.of(2L),List.of(0L)).isEmpty(),"reversed device must not conduct");
        check(PotentialCircuit.solve(forward,List.of(0L),List.of(9L)).isEmpty(),"unreachable low terminal must not activate partial path");
        System.out.println("PASS: open circuits, attenuation boundaries, long lines, multiple sources, cycles and shortest paths");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
