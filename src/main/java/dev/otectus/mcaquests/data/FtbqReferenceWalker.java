package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqChapterCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqQuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqTaskCompletedCondition;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.FtbqProgressReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, FTB-free walk of a single {@link QuestDefinition}'s {@code ftbq_*} conditions/objective/reward
 * references — the MCA → book half of {@code /mcaquests ftbq validate} (spec §21). Deliberately walks
 * the <em>author</em> {@link QuestDefinition#conditions()}, not {@link QuestDefinition#effectiveConditions()}:
 * the latter injects synthetic {@code quest_completed} (chain prerequisite) and
 * {@code not(ftbq_quest_completed)} (block_offer desugar) entries that already duplicate the chain
 * prerequisite / {@code ftbq_complete_quest} objective this class walks separately, so using it here
 * would double-count and would fabricate a reference for a field the author never wrote.
 *
 * <p>No FTB imports and no {@link dev.otectus.mcaquests.compat.FtbqBridge} calls — this only extracts
 * <em>what</em> is referenced; whether a reference resolves is the caller's job (via the bridge's
 * {@code questIdExists}/{@code chapterIdExists}/{@code taskIdExists}), which keeps this class runnable
 * with no FTB jars and no bridge state, and cheaply unit-testable with stub definitions.
 */
public final class FtbqReferenceWalker {

    private FtbqReferenceWalker() {
    }

    /** Which {@link dev.otectus.mcaquests.compat.FtbqBridge} existence check a {@link Reference} needs. */
    public enum Kind {
        QUEST, CHAPTER, TASK
    }

    /** One MCA → book reference: {@code questId} is the MCA quest that authored it, not an FTB id. */
    public record Reference(ResourceLocation questId, String field, String hexId, Kind kind) {
    }

    /** Every {@code ftbq_*} reference {@code def} makes, in author order (conditions, then objectives, then rewards). */
    public static List<Reference> collect(QuestDefinition def) {
        List<Reference> refs = new ArrayList<>();
        def.conditions().ifPresent(condition -> walkCondition(def.id(), "conditions", condition, refs));

        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i) instanceof FtbqCompleteQuestObjective o) {
                refs.add(new Reference(def.id(), "objectives[" + i + "].quest", o.quest(), Kind.QUEST));
            }
        }

        List<QuestReward> rewards = def.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i) instanceof FtbqProgressReward r) {
                Kind kind = r.action() == FtbqProgressReward.ProgressAction.COMPLETE_QUEST ? Kind.QUEST : Kind.TASK;
                refs.add(new Reference(def.id(), "rewards[" + i + "].id", r.id(), kind));
            }
        }
        return refs;
    }

    private static void walkCondition(ResourceLocation questId, String path, QuestCondition condition,
                                       List<Reference> out) {
        if (condition instanceof AllOfCondition all) {
            List<QuestCondition> children = all.conditions();
            for (int i = 0; i < children.size(); i++) {
                walkCondition(questId, path + ".all_of[" + i + "]", children.get(i), out);
            }
        } else if (condition instanceof AnyOfCondition any) {
            List<QuestCondition> children = any.conditions();
            for (int i = 0; i < children.size(); i++) {
                walkCondition(questId, path + ".any_of[" + i + "]", children.get(i), out);
            }
        } else if (condition instanceof NotCondition not) {
            walkCondition(questId, path + ".not", not.condition(), out);
        } else if (condition instanceof FtbqQuestCompletedCondition c) {
            out.add(new Reference(questId, path + ".quest", c.quest(), Kind.QUEST));
        } else if (condition instanceof FtbqChapterCompletedCondition c) {
            out.add(new Reference(questId, path + ".chapter", c.chapter(), Kind.CHAPTER));
        } else if (condition instanceof FtbqTaskCompletedCondition c) {
            out.add(new Reference(questId, path + ".task", c.task(), Kind.TASK));
        }
    }
}
