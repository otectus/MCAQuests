package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.RelatedVillagerStatusCondition;
import dev.otectus.mcaquests.quest.objective.CureVillagerObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.VillagerTargeted;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.target.VillagerTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Refuses to let a quest name a villager it has not established exists (spec invariant I2).
 *
 * <p>The defect this catches shipped in the bundled pack and reached players: a quest whose objective
 * delivered a letter to {@code {"mode": "family", "relation": "sibling"}} was gated on
 * {@code related_villager_status sibling/same_village} — which sounds airtight and was not, because the
 * status was evaluated against a village resident roll that MCA never prunes on death. A second file,
 * {@code lost_child/2_deeper}, had no {@code conditions} block whatsoever and leaned entirely on its chain
 * prerequisite, which says "you finished stage 1 with this villager" and nothing about whether the missing
 * child is still missing. Two situations had the same gap.
 *
 * <p>The runtime fix ({@code require} on the target, checked by {@code OfferFilters}) means a pack with no
 * gate no longer misleads a <em>player</em> — the quest is simply never offered. But silently never
 * offering a quest is its own kind of mystery, and the pack author is the only person who can fix it. This
 * turns it into a message they can read.
 *
 * <h2>Severity</h2>
 *
 * <p>Findings are <b>errors under {@code strictJsonValidation} and warnings otherwise</b>, decided by the
 * loader that calls this. That is deliberate for one release: the check is new and it rejects third-party
 * content that has always loaded, so an author gets a loud, specific, actionable line rather than a server
 * that will not start. The bundled pack is held to the strict standard at build time by
 * {@code BuiltinFamilyGateTest}, because a player cannot fix a broken built-in.
 */
public final class TargetGateValidator {

    /**
     * Statuses that cannot both hold of one villager, so a gate promising one can never satisfy a target
     * requiring the other. Everything absent from this map is either compatible or merely weaker.
     *
     * <p>{@code alive} and {@code missing} are deliberately <em>not</em> here: a missing relative is alive,
     * which is exactly why a missing-kin quest can be about them.
     */
    private static final Map<String, Set<String>> DISJOINT = Map.of(
            "dead", Set.of("alive", "reachable", "nearby", "same_village", "missing", "infected"),
            "alive", Set.of("dead"),
            "reachable", Set.of("dead", "missing"),
            "nearby", Set.of("dead", "missing"),
            "same_village", Set.of("dead", "missing"),
            "missing", Set.of("dead", "reachable", "nearby", "same_village", "infected"),
            // Infection is read off a loaded body, so an infected relative is neither gone nor departed.
            "infected", Set.of("dead", "missing"));

    /** The relations {@code any} is the union of. Grandparent is excluded, as it is in the walk itself. */
    private static final Set<String> IMMEDIATE = Set.of("spouse", "parent", "child", "sibling");

    /**
     * Relations a villager can have at most one of.
     *
     * <p>Only these can produce a genuine contradiction between a gate and a target. A giver may perfectly
     * well have one sibling who is dead and another who is alive next door, so "gated on sibling/dead,
     * targets a reachable sibling" is odd but possible; {@code widow_memorial} is exactly that shape one
     * step further out, gated on a dead spouse while targeting any reachable relative. A spouse is
     * different: there is only ever one, so requiring them to be both dead and findable is not odd, it is
     * impossible.
     */
    private static final Set<String> SINGLE_VALUED = Set.of("spouse");

    /** The status that answers "is this relative actually turning?" — see {@link #checkCureGate}. */
    private static final String INFECTED = "infected";

    private TargetGateValidator() {
    }

    /** Every gate problem in the loaded quests, as human-readable lines. */
    public static void validate(Map<net.minecraft.resources.ResourceLocation, QuestDefinition> quests,
                                List<String> errors, List<String> warnings) {
        for (QuestDefinition quest : quests.values()) {
            check("Quest '" + quest.id() + "'", quest.objectives(), quest.effectiveConditions(),
                    errors, warnings);
        }
    }

    /**
     * The same check over situation offers.
     *
     * <p>Situation JSON had no cross-reference validation at all, which is precisely why two of the
     * shipped situations carried an ungated family target for several releases without anyone noticing.
     */
    public static void validateSituations(Collection<SituationDefinition> situations,
                                          List<String> errors, List<String> warnings) {
        for (SituationDefinition situation : situations) {
            check("Situation '" + situation.id() + "'", situation.offer().objectives(),
                    situation.offer().conditions(), errors, warnings);
        }
    }

    private static void check(String label, List<QuestObjective> objectives,
                              Optional<QuestCondition> conditions, List<String> errors,
                              List<String> warnings) {
        List<RelatedVillagerStatusCondition> gates = requiredGates(conditions);
        for (int index = 0; index < objectives.size(); index++) {
            if (!(objectives.get(index) instanceof VillagerTargeted targeted)) {
                continue;
            }
            VillagerTarget target = targeted.targetSelector();
            if (target.mode() != VillagerTarget.Mode.FAMILY) {
                continue;
            }
            String where = label + ": objective[" + index + "]";
            String relation = target.effectiveRelation();
            String require = target.effectiveRequire();

            for (RelatedVillagerStatusCondition gate : gates) {
                if (SINGLE_VALUED.contains(relation) && gate.relation().equals(relation)
                        && DISJOINT.getOrDefault(require, Set.of()).contains(gate.status())) {
                    errors.add(where + " targets the giver's " + relation + " and requires them to be '"
                            + require + "', but this definition is gated on status '" + gate.status()
                            + "' for that same relation. A villager has only one " + relation + ", so "
                            + "those can never both be true and the quest could never be offered. Change "
                            + "the 'require' or the gate.");
                }
            }

            checkCureGate(where, objectives.get(index), target, relation, require, gates, errors);

            if (!target.requiresExistence()) {
                continue; // "dead", "missing" and "any_known" assert nothing that needs establishing
            }
            if (gates.stream().noneMatch(gate -> relationCovers(gate.relation(), relation)
                    && !DISJOINT.getOrDefault(require, Set.of()).contains(gate.status()))) {
                errors.add(where + " targets the giver's " + relation + " and requires them to be '"
                        + require + "', but nothing in this definition's conditions establishes that one "
                        + "exists. Add: {\"type\": \"mcaquests:related_villager_status\", \"relation\": \""
                        + relation + "\", \"status\": \"" + require + "\"}. "
                        + "(An 'is_family_member' gate does not count: it asks how the PLAYER is related "
                        + "to the giver, not whether the giver has a findable relative.)");
            } else if (relation.equals("any")) {
                warnings.add(where + " targets 'any' relative. That is allowed, but the candidate order is "
                        + "spouse, then parents, then children, then siblings, so it will usually bind the "
                        + "spouse; name the relation you mean if it matters.");
            }
        }
    }

    /**
     * A {@code cure_villager} objective about a relative has to establish that the relative is infected.
     *
     * <p>Nothing else can. Conditions are evaluated at offer time only, so the gate is the one moment
     * anything asks about the kin's state; without an infection question the quest is offered about a
     * perfectly healthy brother and its objective cannot advance until he happens to be zombified, which
     * is how {@code relations_cure_infected_kin} shipped as the pack's one uncompletable quest.
     * {@code cure_my_spouse} always got this right, with an {@code infected} gate on the giver.
     *
     * <p>Both halves of the gate are accepted: {@code require: "infected"} on the target itself (which the
     * existence check above then insists is gated), or a {@code related_villager_status} of status
     * {@code infected} covering the relation. Severity follows the caller, like everything else here.
     */
    private static void checkCureGate(String where, QuestObjective objective, VillagerTarget target,
                                      String relation, String require,
                                      List<RelatedVillagerStatusCondition> gates, List<String> errors) {
        if (!(objective instanceof CureVillagerObjective) || target.mode() != VillagerTarget.Mode.FAMILY) {
            return;
        }
        if (INFECTED.equals(require)
                || gates.stream().anyMatch(gate -> INFECTED.equals(gate.status())
                        && relationCovers(gate.relation(), relation))) {
            return;
        }
        errors.add(where + " cures the giver's " + relation + ", but nothing in this definition requires "
                + "that they are infected, so it can be offered about a healthy relative and then never "
                + "advance. Set \"require\": \"infected\" on the villager target, or add: "
                + "{\"type\": \"mcaquests:related_villager_status\", \"relation\": \"" + relation
                + "\", \"status\": \"infected\"}.");
    }

    /**
     * The {@code related_villager_status} leaves a gate <em>conjunctively requires</em> — reachable from
     * the root through {@code all_of} but never through an {@code any_of}, whose branches are optional
     * alternatives, and never under a {@code not}, which asserts the opposite.
     *
     * <p>Soundness matters more than completeness here: a leaf this misses produces a false accusation
     * against a pack that is actually correct, which is much worse than missing one genuine gap. Mirrors
     * {@link ConditionRefs#required}, which makes the same trade for the same reason.
     */
    private static List<RelatedVillagerStatusCondition> requiredGates(Optional<QuestCondition> conditions) {
        List<RelatedVillagerStatusCondition> out = new ArrayList<>();
        conditions.ifPresent(condition -> collect(condition, true, out));
        return out;
    }

    private static void collect(QuestCondition condition, boolean polarity,
                                List<RelatedVillagerStatusCondition> out) {
        if (condition instanceof AllOfCondition all) {
            all.conditions().forEach(child -> collect(child, polarity, out));
        } else if (condition instanceof NotCondition not) {
            collect(not.condition(), !polarity, out);
        } else if (condition instanceof AnyOfCondition) {
            // A disjunction guarantees nothing on its own, so it establishes nothing.
        } else if (polarity && condition instanceof RelatedVillagerStatusCondition related) {
            out.add(related);
        }
    }

    /**
     * Whether a gate on {@code gateRelation} establishes something about a target selecting
     * {@code targetRelation}.
     *
     * <p>The same relation obviously does. So does a narrower one: proving a sibling exists proves a member
     * of {@code any} exists, because {@code any} is the union of spouse, parents, children and siblings.
     * The reverse is not true — "some relative is nearby" says nothing about a <em>sibling</em> — and
     * neither direction holds for {@code grandparent}, which the {@code any} walk deliberately excludes.
     */
    private static boolean relationCovers(String gateRelation, String targetRelation) {
        return gateRelation.equals(targetRelation)
                || (targetRelation.equals("any") && IMMEDIATE.contains(gateRelation));
    }

    /** Exposed for the tests that pin the disjointness table against {@link RelativeCandidate}. */
    static boolean disjoint(String a, String b) {
        return DISJOINT.getOrDefault(a, Set.of()).contains(b);
    }
}
