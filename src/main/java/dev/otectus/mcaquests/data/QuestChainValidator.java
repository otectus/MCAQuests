package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Cross-quest validation of relationship-chain metadata, run after all quests load. Appends human-readable
 * problems (naming the affected quest, chain, field, and referenced id) to the {@code /mcaquests validate}
 * output. Definite breakage goes to {@code errors} (these honour {@code strictJsonValidation}); heuristic or
 * stylistic problems go to {@code warnings} (always reported, never fatal).
 */
public final class QuestChainValidator {

    private QuestChainValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors, List<String> warnings) {
        Map<String, Set<Integer>> stageTotalsByChain = new LinkedHashMap<>();

        for (QuestDefinition def : loaded.values()) {
            Optional<ChainSpec> chainOpt = def.chain();
            if (chainOpt.isEmpty()) {
                continue;
            }
            ChainSpec chain = chainOpt.get();
            ResourceLocation id = def.id();
            String where = where(id, chain);

            if (chain.chain().isBlank()) {
                errors.add(where + "chain id is blank.");
            }
            if (chain.chain().indexOf('|') >= 0) {
                errors.add(where + "chain id must not contain '|' (reserved as the id|name separator "
                        + "in the FTB editor known-ids sync, spec section 20).");
            }
            if (chain.stage() < 1) {
                errors.add(where + "chain.stage must be >= 1 (was " + chain.stage() + ").");
            }
            chain.stageTotal().ifPresent(total -> {
                if (chain.stage() > total) {
                    errors.add(where + "chain.stage " + chain.stage() + " exceeds chain.stage_total " + total + ".");
                }
                stageTotalsByChain.computeIfAbsent(chain.chain(), k -> new TreeSet<>()).add(total);
            });
            if (chain.prerequisites().contains(id)) {
                errors.add(where + "chain.prerequisites lists itself.");
            }
            if (chain.unlocks().contains(id)) {
                errors.add(where + "chain.unlocks lists itself.");
            }

            checkReferences(where, "chain.prerequisites", chain.prerequisites(), loaded, errors);
            checkReferences(where, "chain.unlocks", chain.unlocks(), loaded, errors);
            // Branch-condition references on a chain quest (e.g. quest_failed: ...) must also resolve.
            checkReferences(where, "conditions", ConditionRefs.allReferencedQuests(def.conditions()), loaded, errors);

            // A later stage with no prerequisites, no inbound unlock, and no outcome-branch condition can
            // never be reached by any path.
            if (chain.stage() > 1 && chain.prerequisites().isEmpty()
                    && !hasInboundUnlock(id, loaded) && !ConditionRefs.hasOutcomeBranch(def.conditions())) {
                errors.add(where + "chain.stage " + chain.stage() + " is unreachable — no chain.prerequisites, "
                        + "no other quest lists it in chain.unlocks, and no branch condition.");
            }

            checkImpossibleGate(where, def, loaded, errors, warnings);
        }

        // stage_total should agree across the stages of one chain.
        stageTotalsByChain.forEach((chainId, totals) -> {
            if (totals.size() > 1) {
                warnings.add("Chain '" + chainId + "': inconsistent chain.stage_total across stages " + totals + ".");
            }
        });

        checkDuplicateStages(loaded, warnings);
        checkDanglingUnlocks(loaded, warnings);

        GraphCycles.findCycle(buildPrerequisiteGraph(loaded)).ifPresent(cycle ->
                errors.add("Quest chains contain a circular chain.prerequisites reference: " + describeCycle(cycle)));
        GraphCycles.findCycle(buildUnlockGraph(loaded)).ifPresent(cycle ->
                errors.add("Quest chains contain a circular chain.unlocks reference: " + describeCycle(cycle)));
    }

    /** Contradictions (error) and branches gated on a quest that can never fail (warning), over the effective gate. */
    private static void checkImpossibleGate(String where, QuestDefinition def,
                                            Map<ResourceLocation, QuestDefinition> loaded,
                                            List<String> errors, List<String> warnings) {
        List<ConditionRefs.Ref> required = ConditionRefs.required(def.effectiveConditions());

        // "must be completed" and "must be not-completed" of the same quest+scope can never both hold.
        Map<String, Set<Boolean>> completed = new HashMap<>();
        for (ConditionRefs.Ref ref : required) {
            if (ref.kind() == ConditionRefs.Ref.Kind.COMPLETED) {
                completed.computeIfAbsent(ref.quest() + " (" + ref.scope() + ")", k -> new HashSet<>()).add(ref.polarity());
            }
        }
        completed.forEach((quest, polarities) -> {
            if (polarities.contains(true) && polarities.contains(false)) {
                errors.add(where + "impossible gate — requires '" + quest + "' to be both completed and not-completed.");
            }
        });

        // A gate that requires a quest to have failed can never pass if that quest has no failure trigger.
        for (ConditionRefs.Ref ref : required) {
            if (ref.kind() == ConditionRefs.Ref.Kind.FAILED && ref.polarity()) {
                QuestDefinition target = loaded.get(ref.quest());
                if (target != null && !canEverFail(target)) {
                    warnings.add(where + "requires '" + ref.quest() + "' to fail, but '" + ref.quest()
                            + "' has no failure triggers — this can never be offered unless failQuestIfGiverDies is enabled.");
                }
            }
        }
    }

    /** Same chain + same stage with more than one non-branching quest: they would be offered together (ambiguous). */
    private static void checkDuplicateStages(Map<ResourceLocation, QuestDefinition> loaded, List<String> warnings) {
        Map<String, List<QuestDefinition>> byChainStage = new LinkedHashMap<>();
        for (QuestDefinition def : loaded.values()) {
            if (def.enabled()) {
                def.chain().ifPresent(chain -> byChainStage
                        .computeIfAbsent(chain.chain() + " @ stage " + chain.stage(), k -> new ArrayList<>()).add(def));
            }
        }
        byChainStage.forEach((group, defs) -> {
            if (defs.size() < 2) {
                return;
            }
            long nonBranching = defs.stream().filter(d -> !ConditionRefs.hasOutcomeBranch(d.conditions())).count();
            if (nonBranching > 1) {
                String ids = defs.stream().map(d -> d.id().toString()).collect(Collectors.joining(", "));
                warnings.add("Chain " + group + " has " + defs.size() + " quests (" + ids + ") and " + nonBranching
                        + " lack a mutually-exclusive outcome branch — they may be offered together; selection is then weighted.");
            }
        });
    }

    /** {@code unlocks: [Y]} where Y's gate never references the unlocking quest — the link is cosmetic. */
    private static void checkDanglingUnlocks(Map<ResourceLocation, QuestDefinition> loaded, List<String> warnings) {
        for (QuestDefinition def : loaded.values()) {
            def.chain().ifPresent(chain -> {
                for (ResourceLocation target : chain.unlocks()) {
                    QuestDefinition downstream = loaded.get(target);
                    if (downstream != null && downstream.enabled() && !references(downstream, def.id())) {
                        warnings.add(where(def.id(), chain) + "chain.unlocks '" + target + "', but '" + target
                                + "' has no prerequisite or condition referencing '" + def.id() + "' (the link is cosmetic).");
                    }
                }
            });
        }
    }

    private static boolean references(QuestDefinition quest, ResourceLocation target) {
        boolean inPrereqs = quest.chain().map(c -> c.prerequisites().contains(target)).orElse(false);
        return inPrereqs || ConditionRefs.allReferencedQuests(quest.conditions()).contains(target);
    }

    private static boolean canEverFail(QuestDefinition def) {
        return def.failure().map(FailureSpec::hasTrigger).orElse(false);
    }

    private static void checkReferences(String where, String field, List<ResourceLocation> refs,
                                        Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        for (ResourceLocation ref : refs) {
            QuestDefinition target = loaded.get(ref);
            if (target == null) {
                errors.add(where + field + " references unknown quest '" + ref + "'.");
            } else if (!target.enabled()) {
                errors.add(where + field + " references disabled quest '" + ref + "'.");
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

    private static Map<ResourceLocation, List<ResourceLocation>> buildPrerequisiteGraph(
            Map<ResourceLocation, QuestDefinition> loaded) {
        Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
        for (QuestDefinition def : loaded.values()) {
            def.chain().ifPresent(chain -> graph.put(def.id(),
                    chain.prerequisites().stream().filter(loaded::containsKey).toList()));
        }
        return graph;
    }

    private static String where(ResourceLocation id, ChainSpec chain) {
        return "Quest '" + id + "' (chain '" + chain.chain() + "', stage " + chain.stage() + "): ";
    }

    private static String describeCycle(List<ResourceLocation> cycle) {
        return String.join(" -> ", cycle.stream().map(ResourceLocation::toString).toList());
    }

    /**
     * Detects a cycle in the {@code unlocks} graph and returns one offending path (closed, so the first and
     * last node match), or empty if acyclic. Retained for direct unit testing; delegates to {@link GraphCycles}.
     */
    public static Optional<List<ResourceLocation>> findUnlockCycle(Map<ResourceLocation, List<ResourceLocation>> graph) {
        return GraphCycles.findCycle(graph);
    }
}
