package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-quest validation of relationship-chain metadata, run after all quests load. Appends
 * human-readable problems (naming the affected quest and field) to the same error list surfaced by
 * {@code /mcaquests validate}. None of this is fatal on its own — it just reports.
 */
public final class QuestChainValidator {

    private QuestChainValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        for (QuestDefinition def : loaded.values()) {
            Optional<ChainSpec> chainOpt = def.chain();
            if (chainOpt.isEmpty()) {
                continue;
            }
            ChainSpec chain = chainOpt.get();
            ResourceLocation id = def.id();

            if (chain.stage() < 1) {
                errors.add("Quest '" + id + "': chain.stage must be >= 1 (was " + chain.stage() + ").");
            }
            checkReferences(id, "chain.prerequisites", chain.prerequisites(), loaded, errors);
            checkReferences(id, "chain.unlocks", chain.unlocks(), loaded, errors);

            // A later stage that nothing unlocks and that has no prerequisites can never be reached.
            if (chain.stage() > 1 && chain.prerequisites().isEmpty() && !hasInboundUnlock(id, loaded)) {
                errors.add("Quest '" + id + "': chain.stage " + chain.stage()
                        + " is unreachable — it has no chain.prerequisites and no other quest lists it in chain.unlocks.");
            }
        }

        findUnlockCycle(buildUnlockGraph(loaded)).ifPresent(cycle ->
                errors.add("Quest chains contain a circular chain.unlocks reference: " + describeCycle(cycle)));
    }

    private static void checkReferences(ResourceLocation owner, String field, List<ResourceLocation> refs,
                                        Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        for (ResourceLocation ref : refs) {
            QuestDefinition target = loaded.get(ref);
            if (target == null) {
                errors.add("Quest '" + owner + "': " + field + " references unknown quest '" + ref + "'.");
            } else if (!target.enabled()) {
                errors.add("Quest '" + owner + "': " + field + " references disabled quest '" + ref + "'.");
            }
        }
    }

    private static boolean hasInboundUnlock(ResourceLocation id, Map<ResourceLocation, QuestDefinition> loaded) {
        return loaded.values().stream()
                .anyMatch(def -> def.chain().map(chain -> chain.unlocks().contains(id)).orElse(false));
    }

    private static Map<ResourceLocation, List<ResourceLocation>> buildUnlockGraph(
            Map<ResourceLocation, QuestDefinition> loaded) {
        Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
        for (QuestDefinition def : loaded.values()) {
            def.chain().ifPresent(chain -> graph.put(def.id(),
                    chain.unlocks().stream().filter(loaded::containsKey).toList()));
        }
        return graph;
    }

    private static String describeCycle(List<ResourceLocation> cycle) {
        return String.join(" -> ", cycle.stream().map(ResourceLocation::toString).toList());
    }

    /**
     * Detects a cycle in the {@code unlocks} graph and returns one offending path (closed, so the first
     * and last node match), or empty if the graph is acyclic. Standard DFS with a gray (on-path) set.
     * Side-effect-free so it can be unit-tested directly.
     */
    public static Optional<List<ResourceLocation>> findUnlockCycle(Map<ResourceLocation, List<ResourceLocation>> graph) {
        Set<ResourceLocation> done = new HashSet<>();
        for (ResourceLocation node : graph.keySet()) {
            Optional<List<ResourceLocation>> cycle = dfs(node, graph, done, new LinkedHashSet<>());
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private static Optional<List<ResourceLocation>> dfs(ResourceLocation node,
                                                        Map<ResourceLocation, List<ResourceLocation>> graph,
                                                        Set<ResourceLocation> done,
                                                        LinkedHashSet<ResourceLocation> path) {
        if (path.contains(node)) {
            List<ResourceLocation> ordered = new ArrayList<>(path);
            List<ResourceLocation> cycle = new ArrayList<>(ordered.subList(ordered.indexOf(node), ordered.size()));
            cycle.add(node); // close the loop for a readable a -> b -> a message
            return Optional.of(cycle);
        }
        if (done.contains(node)) {
            return Optional.empty();
        }
        path.add(node);
        for (ResourceLocation next : graph.getOrDefault(node, List.of())) {
            Optional<List<ResourceLocation>> cycle = dfs(next, graph, done, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.remove(node);
        done.add(node);
        return Optional.empty();
    }
}
