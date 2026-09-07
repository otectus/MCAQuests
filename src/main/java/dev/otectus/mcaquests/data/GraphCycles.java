package dev.otectus.mcaquests.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generic directed-cycle detection shared by quest-chain and project validation. Returns one offending
 * path (closed, so the first and last node match) or empty if the graph is acyclic. Standard DFS with a
 * gray (on-path) set. Side-effect-free for direct unit testing.
 */
public final class GraphCycles {

    private GraphCycles() {
    }

    public static <K> Optional<List<K>> findCycle(Map<K, List<K>> graph) {
        Set<K> done = new HashSet<>();
        for (K node : graph.keySet()) {
            if (done.contains(node)) continue;
            // Datapack chains can be arbitrarily long. Keep DFS frames on the heap instead of
            // exhausting the server's call stack during reload of a large, otherwise valid pack.
            Map<K, Integer> path = new LinkedHashMap<>();
            Deque<Frame<K>> frames = new ArrayDeque<>();
            path.put(node, 0);
            frames.push(new Frame<>(node, graph.getOrDefault(node, List.of()).iterator()));
            while (!frames.isEmpty()) {
                Frame<K> frame = frames.peek();
                if (!frame.edges().hasNext()) {
                    frames.pop();
                    path.remove(frame.node());
                    done.add(frame.node());
                    continue;
                }
                K next = frame.edges().next();
                Integer start = path.get(next);
                if (start != null) {
                    List<K> ordered = new ArrayList<>(path.keySet());
                    List<K> cycle = new ArrayList<>(ordered.subList(start, ordered.size()));
                    cycle.add(next);
                    return Optional.of(cycle);
                }
                if (!done.contains(next)) {
                    path.put(next, path.size());
                    frames.push(new Frame<>(next, graph.getOrDefault(next, List.of()).iterator()));
                }
            }
        }
        return Optional.empty();
    }

    private record Frame<K>(K node, Iterator<K> edges) { }
}
