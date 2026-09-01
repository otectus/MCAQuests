package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadBuildings;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.TownsteadWorkforceProjectObjective;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadAvailableCondition;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadProfessionProgressObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadSpiritProgressObjective;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.TownsteadReward;
import dev.otectus.mcaquests.quest.reward.TownsteadSkillReward;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks loaded content against the Townstead that is actually running (spec §5.11).
 *
 * <p>Every other validator in this package answers a question about the JSON. This one answers a
 * question about the <em>world</em>: can this quest be finished by the Townstead installed on this
 * server, with the datapacks this server has loaded? That is a different question and it has a
 * different answer on every install, which is why it runs after a reload rather than at build time.
 *
 * <p>It exists because of a defect that shipped. Townstead answers a progression query for every
 * profession, including the ones it has no progression for, so 1.4.0 offered a fisherman 120
 * profession XP that no Townstead work task ever awards. The quest parsed, validated, was offered, was
 * accepted, and then waited forever. Nothing in the JSON was wrong. Only the world could have said so.
 *
 * <p><b>Findings are warnings, not errors.</b> A registry can change under a running server — a
 * datapack reload can add or remove a profession track — so a definition that is unreachable right now
 * may be reachable in a minute, and refusing to load it would be worse than hiding it. The runtime
 * gates do the hiding; this tells the operator <em>why</em> something has gone quiet.
 *
 * <p>Bundled content is held to the stricter standard at build time instead, by
 * {@code BuiltinTownsteadAchievabilityTest}: a player cannot fix a broken built-in, so it must never
 * ship.
 */
public final class TownsteadContentValidator {

    private TownsteadContentValidator() {
    }

    /**
     * Every achievability problem in the loaded content, as human-readable lines.
     *
     * <p>Returns nothing at all when Townstead is absent. That is not a gap: without Townstead, no
     * Townstead content is eligible in the first place, and reporting a hundred "unreachable" lines on
     * an install that was never going to run them would bury the findings that matter.
     */
    public static List<String> collectWarnings(Collection<QuestDefinition> quests,
                                               Collection<ProjectDefinition> projects,
                                               Collection<SituationDefinition> situations) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (QuestDefinition quest : quests) {
            checkQuest(bridge, quest, warnings);
        }
        for (ProjectDefinition project : projects) {
            checkProject(bridge, project, warnings);
        }
        for (SituationDefinition situation : situations) {
            checkObjectives(bridge, "Situation '" + situation.id() + "'",
                    situation.offer().objectives(),
                    declaredCapabilities(situation.offer().conditions()), warnings);
        }
        return List.copyOf(warnings);
    }

    /** Logs the findings once after a reload, so an operator sees them without running a command. */
    public static void report(List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        McaQuests.LOGGER.warn("[MCA: Quests] {} definition(s) cannot currently be finished with the "
                + "loaded Townstead. They are hidden rather than offered; run "
                + "'/mcaquests validate' for the list.", warnings.size());
        warnings.forEach(warning -> McaQuests.LOGGER.warn("[MCA: Quests]   {}", warning));
    }

    // --- quests -----------------------------------------------------------------------------

    private static void checkQuest(TownsteadBridge bridge, QuestDefinition quest, List<String> out) {
        String label = "Quest '" + quest.id() + "'";
        Set<TownsteadCapability> declared = declaredCapabilities(quest.effectiveConditions());
        checkObjectives(bridge, label, quest.objectives(), declared, out);
        checkRewards(bridge, label, quest.rewards(), out);
        checkPeriodRepeat(label, quest, declared, out);
        checkDifficultyShape(label, quest, out);
        checkDuplicateObjectives(label, quest.objectives(), out);
    }

    /**
     * The capabilities a definition's {@code townstead_available} gates promise. An objective that
     * reads more than its quest declares will suspend the moment that capability is missing, which
     * looks to a player like a quest that stopped working for no reason.
     */
    private static Set<TownsteadCapability> declaredCapabilities(Optional<QuestCondition> conditions) {
        EnumSet<TownsteadCapability> declared = EnumSet.noneOf(TownsteadCapability.class);
        conditions.ifPresent(condition -> collectCapabilities(condition, declared));
        return declared;
    }

    /**
     * Walks a condition tree for {@code townstead_available} gates.
     *
     * <p>Hand-rolled rather than routed through {@code ConditionRefs}, which walks quest references
     * rather than leaves. Composites are matched structurally because they deliberately have no type
     * id -- {@code all_of} is a key, not a registered type.
     */
    private static void collectCapabilities(QuestCondition condition,
                                            Set<TownsteadCapability> into) {
        if (condition instanceof TownsteadAvailableCondition available) {
            into.addAll(available.capabilities());
            return;
        }
        if (condition instanceof AllOfCondition all) {
            all.conditions().forEach(child -> collectCapabilities(child, into));
        } else if (condition instanceof AnyOfCondition any) {
            // Deliberately unioned rather than intersected: a gate that only some branches declare is
            // still a gate the objective may end up relying on, and the point here is to catch the
            // objective that reads something nothing declared.
            any.conditions().forEach(child -> collectCapabilities(child, into));
        } else if (condition instanceof NotCondition not) {
            collectCapabilities(not.condition(), into);
        }
    }

    private static void checkObjectives(TownsteadBridge bridge, String label,
                                        List<QuestObjective> objectives,
                                        Set<TownsteadCapability> declared, List<String> out) {
        for (int index = 0; index < objectives.size(); index++) {
            QuestObjective objective = objectives.get(index);
            String where = label + ": objective[" + index + "]";
            if (objective instanceof TownsteadObjective townstead) {
                for (TownsteadCapability capability : townstead.requiredCapabilities()) {
                    if (!declared.contains(capability)) {
                        out.add(where + " reads " + capability + " but no townstead_available gate on "
                                + "this definition declares it, so it will suspend rather than hide.");
                    }
                }
            }
            if (objective instanceof TownsteadProfessionProgressObjective progress) {
                checkProfessionGoal(bridge, where, progress, out);
            }
            if (objective instanceof TownsteadSpiritProgressObjective spirit) {
                spirit.spirit().ifPresent(id -> {
                    if (!bridge.isKnownSpirit(id)) {
                        out.add(where + " asks for spirit '" + id + "', which the loaded Townstead "
                                + "does not register.");
                    }
                });
            }
        }
    }

    /**
     * The check this whole class was written for: can the named trade reach what is being asked?
     *
     * <p>Only reported when {@code READ_PROFESSION_SPEC} actually bound. Without it "no track" and
     * "cannot tell" are indistinguishable, and warning on the second would fill the log of every
     * server running an older Townstead with findings it can do nothing about.
     */
    private static void checkProfessionGoal(TownsteadBridge bridge, String where,
                                            TownsteadProfessionProgressObjective progress,
                                            List<String> out) {
        if (!bridge.has(TownsteadCapability.READ_PROFESSION_SPEC)) {
            return;
        }
        String profession = progress.profession().orElse("");
        if (profession.isEmpty()) {
            return; // binds whatever the giver practises; only answerable once a giver is known
        }
        TownsteadProfessionTrackView track = bridge.professionTrack(profession);
        if (!track.progressive()) {
            out.add(where + " asks " + profession + " to advance, but the loaded Townstead has no "
                    + "progression for that trade, so it could never finish.");
            return;
        }
        progress.targetTier().ifPresent(tier -> {
            if (!track.supportsTier(tier)) {
                out.add(where + " asks " + profession + " to reach tier " + tier
                        + ", but the loaded track stops at " + track.maxTier() + ".");
            }
        });
        progress.targetXp().ifPresent(xp -> {
            if (xp > track.maxXp()) {
                out.add(where + " asks " + profession + " for " + xp + " XP, but the loaded track "
                        + "caps at " + track.maxXp() + ".");
            }
        });
    }

    private static void checkRewards(TownsteadBridge bridge, String label,
                                     List<QuestReward> rewards, List<String> out) {
        for (int index = 0; index < rewards.size(); index++) {
            QuestReward reward = rewards.get(index);
            String where = label + ": reward[" + index + "]";
            if (reward instanceof TownsteadSkillReward skill
                    && bridge.has(TownsteadCapability.READ_SKILL_REGISTRY)
                    && !bridge.isKnownSkill(skill.skill())) {
                out.add(where + " teaches skill '" + skill.skill() + "', which the loaded Townstead "
                        + "does not register; it would report success and change nothing.");
            }
            if (reward instanceof TownsteadReward townstead
                    && !bridge.has(townstead.capability())) {
                out.add(where + " needs " + townstead.capability() + ", which did not bind.");
            }
        }
    }

    /**
     * A calendar-relative repeat rule with no calendar gate will fall back to its tick cooldown on any
     * install where {@code READ_CALENDAR} is missing, which is a silently different quest.
     */
    private static void checkPeriodRepeat(String label, QuestDefinition quest,
                                          Set<TownsteadCapability> declared, List<String> out) {
        if (quest.repeat().isPeriodic() && !declared.contains(TownsteadCapability.READ_CALENDAR)) {
            out.add(label + " repeats by Townstead calendar period but declares no READ_CALENDAR gate, "
                    + "so it will silently fall back to its tick cooldown.");
        }
    }

    /**
     * Spec §3.1: at least half of medium and hard content must do something other than carry items. A
     * hard quest that is only fetching is a bulk errand wearing a difficulty band.
     */
    private static void checkDifficultyShape(String label, QuestDefinition quest, List<String> out) {
        boolean hard = quest.difficulty().map(difficulty -> difficulty.name().equalsIgnoreCase("hard"))
                .orElse(false);
        if (!hard || quest.objectives().isEmpty()) {
            return;
        }
        boolean everythingIsCarrying = quest.objectives().stream()
                .allMatch(objective -> isPossession(objective.type().id()));
        if (everythingIsCarrying) {
            out.add(label + " is marked hard but every objective is possession or delivery; a hard "
                    + "quest should ask for something the player has to go and do.");
        }
    }

    private static boolean isPossession(ResourceLocation type) {
        String path = type.getPath();
        return path.equals("item_delivery") || path.equals("obtain_item")
                || path.equals("deliver_to_villager") || path.equals("craft_item");
    }

    /**
     * Two objectives with the same type and the same arguments are almost always an authoring slip —
     * a stage duplicated during editing — and the second one is invisible to a player, who sees two
     * identical lines and no way to tell them apart.
     */
    private static void checkDuplicateObjectives(String label, List<QuestObjective> objectives,
                                                 List<String> out) {
        Set<String> seen = new LinkedHashSet<>();
        for (QuestObjective objective : objectives) {
            String fingerprint = objective.type().id() + "|" + objective.describe().getString();
            if (!seen.add(fingerprint)) {
                out.add(label + " has two objectives that read identically (" + fingerprint
                        + "); a player cannot tell them apart.");
            }
        }
    }

    // --- projects ---------------------------------------------------------------------------

    private static void checkProject(TownsteadBridge bridge, ProjectDefinition project,
                                     List<String> out) {
        if (!bridge.has(TownsteadCapability.READ_PROFESSION_SPEC)) {
            return;
        }
        for (var phase : project.phases()) {
            for (ProjectObjective objective : phase.objectives()) {
                if (!(objective instanceof TownsteadWorkforceProjectObjective workforce)) {
                    continue;
                }
                boolean anyReachable = workforce.professions().stream()
                        .map(bridge::professionTrack)
                        .anyMatch(track -> track.supportsTier(workforce.minimumTier()));
                if (!anyReachable) {
                    out.add("Project '" + project.id() + "' phase '" + phase.key() + "' asks for tier "
                            + workforce.minimumTier() + " workers, but none of the professions it names "
                            + "can reach that tier in the loaded Townstead.");
                }
            }
        }
    }

    /**
     * Building families this content anchors at that the normalisation table has never heard of.
     *
     * <p>Separate from the checks above because it is answerable without Townstead at all: a building
     * family is an MCA registry id, and a typo in one is a typo whether or not Townstead is installed.
     */
    public static List<String> unknownBuildingFamilies(Collection<String> families) {
        List<String> unknown = new ArrayList<>();
        for (String family : families) {
            if (!TownsteadBuildings.isKnownFamily(family)) {
                unknown.add(family);
            }
        }
        return List.copyOf(unknown);
    }
}
