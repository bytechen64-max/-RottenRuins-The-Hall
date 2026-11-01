package org.bytechen.rottenruins.overworld.energy;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** 无世界依赖的网络求解器；图节点是导线坐标，距离为沿线最短路径。 */
public final class PotentialCircuit {
    public static final int BLOCKS_PER_POWER_LEVEL = 4;

    private PotentialCircuit() {}

    /** 有向设备必须同时能从高势端到达、并继续通往低势端，才算闭合线路。 */
    public static Map<Long, Integer> solve(Map<Long, List<Long>> graph, Collection<Long> highContacts,
                                           Collection<Long> lowContacts) {
        Map<Long, List<Long>> reverse = new HashMap<>();
        graph.forEach((node, neighbors) -> neighbors.forEach(other ->
                reverse.computeIfAbsent(other, ignored -> new java.util.ArrayList<>()).add(node)));
        Set<Long> reachesLow = new HashSet<>(lowContacts);
        ArrayDeque<Long> queue = new ArrayDeque<>(lowContacts);
        while (!queue.isEmpty()) {
            for (long previous : reverse.getOrDefault(queue.remove(), List.of())) {
                if (reachesLow.add(previous)) queue.add(previous);
            }
        }
        Map<Long, Integer> result = solve(graph, highContacts, !lowContacts.isEmpty());
        result.keySet().retainAll(reachesLow);
        return result;
    }

    public static Map<Long, Integer> solve(Map<Long, List<Long>> graph, Collection<Long> highContacts,
                                           boolean hasLowContact) {
        Map<Long, Integer> result = new HashMap<>();
        if (!hasLowContact) return result;
        Map<Long, Integer> distances = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long contact : highContacts) {
            if (graph.containsKey(contact) && distances.putIfAbsent(contact, 0) == null) queue.add(contact);
        }
        while (!queue.isEmpty()) {
            long current = queue.remove();
            int distance = distances.get(current);
            result.put(current, Math.max(1, 15 - distance / BLOCKS_PER_POWER_LEVEL));
            for (long neighbor : graph.getOrDefault(current, List.of())) {
                if (graph.containsKey(neighbor) && distances.putIfAbsent(neighbor, distance + 1) == null) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }
}
