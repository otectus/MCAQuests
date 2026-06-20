package dev.otectus.mcaquests.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
            Optional<List<K>> cycle = dfs(node, graph, done, new LinkedHashSet<>());
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private static <K> Optional<List<K>> dfs(K node, Map<K, List<K>> graph, Set<K> done, LinkedHashSet<K> path) {
        if (path.contains(node)) {
            List<K> ordered = new ArrayList<>(path);
            List<K> cycle = new ArrayList<>(ordered.subList(ordered.indexOf(node), ordered.size()));
            cycle.add(node); // close the loop for a readable a -> b -> a message
            return Optional.of(cycle);
        }
        if (done.contains(node)) {
            return Optional.empty();
        }
        path.add(node);
        for (K next : graph.getOrDefault(node, List.of())) {
            Optional<List<K>> cycle = dfs(next, graph, done, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.remove(node);
        done.add(node);
        return Optional.empty();
    }
}
