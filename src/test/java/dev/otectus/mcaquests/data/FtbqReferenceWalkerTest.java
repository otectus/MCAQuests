package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqChapterCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqQuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqTaskCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqWhenMissing;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.objective.AlreadyCompleteMode;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.FtbqProgressReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link FtbqReferenceWalker#collect} — the MCA → book half of
 * {@code /mcaquests ftbq validate} (spec §21, task M5.3). No FTB imports, no {@code FtbqBridge}:
 * this class only extracts <em>what</em> a quest definition references, so it is testable with plain
 * stub definitions and runs with no FTB jars on the classpath (mirrors
 * {@code QuestDefinitionEffectiveConditionsTest}'s approach).
 */
class FtbqReferenceWalkerTest {

    private static final ResourceLocation QUEST_ID = ResourceLocation.fromNamespaceAndPath("mcaquests", "archivist_bonus");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestDefinition definitionWith(Optional<QuestCondition> conditions,
                                                   List<QuestObjective> objectives,
                                                   List<QuestReward> rewards) {
        return new QuestDefinition(QUEST_ID, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                GiverSpec.ANY, Map.of(), objectives, rewards, TurnInSpec.DEFAULT, conditions, Optional.empty(),
                Optional.empty(), Optional.empty(), OfferShaping.NONE, dev.otectus.mcaquests.quest.reputation.QuestReputationBlock.NONE);
    }

    @Test
    void emptyDefinitionHasNoReferences() {
        QuestDefinition def = definitionWith(Optional.empty(), List.of(), List.of());
        assertTrue(FtbqReferenceWalker.collect(def).isEmpty());
    }

    @Test
    void topLevelFtbqConditionsAreCollectedWithTheirKindAndField() {
        QuestCondition quest = new FtbqQuestCompletedCondition("1A2B3C4D5E6F7081", FtbqWhenMissing.NOT_MET);
        QuestDefinition def = definitionWith(Optional.of(quest), List.of(), List.of());

        List<FtbqReferenceWalker.Reference> refs = FtbqReferenceWalker.collect(def);
        assertEquals(1, refs.size());
        FtbqReferenceWalker.Reference ref = refs.get(0);
        assertEquals(QUEST_ID, ref.questId());
        assertEquals("conditions.quest", ref.field());
        assertEquals("1A2B3C4D5E6F7081", ref.hexId());
        assertEquals(FtbqReferenceWalker.Kind.QUEST, ref.kind());
    }

    @Test
    void nonFtbqConditionsAreIgnored() {
        QuestCondition other = new QuestCompletedCondition(ResourceLocation.fromNamespaceAndPath("mcaquests", "other"), HistoryScope.GLOBAL);
        QuestDefinition def = definitionWith(Optional.of(other), List.of(), List.of());
        assertTrue(FtbqReferenceWalker.collect(def).isEmpty());
    }

    @Test
    void nestedCombinatorsAreWalkedWithPathAndKindPreserved() {
        QuestCondition chapter = new FtbqChapterCompletedCondition("0123456789ABCDEF", FtbqWhenMissing.NOT_MET);
        QuestCondition task = new FtbqTaskCompletedCondition("F00DF00DF00DF00D", FtbqWhenMissing.MET);
        QuestCondition tree = new AllOfCondition(List.of(
                new NotCondition(chapter),
                new AnyOfCondition(List.of(task))));
        QuestDefinition def = definitionWith(Optional.of(tree), List.of(), List.of());

        List<FtbqReferenceWalker.Reference> refs = FtbqReferenceWalker.collect(def);
        assertEquals(2, refs.size());

        assertTrue(refs.stream().anyMatch(r -> r.kind() == FtbqReferenceWalker.Kind.CHAPTER
                && r.hexId().equals("0123456789ABCDEF")
                && r.field().equals("conditions.all_of[0].not.chapter")));
        assertTrue(refs.stream().anyMatch(r -> r.kind() == FtbqReferenceWalker.Kind.TASK
                && r.hexId().equals("F00DF00DF00DF00D")
                && r.field().equals("conditions.all_of[1].any_of[0].task")));
    }

    @Test
    void ftbqCompleteQuestObjectiveIsCollectedAsQuestKindWithIndexedField() {
        FtbqCompleteQuestObjective objective = new FtbqCompleteQuestObjective(
                "1A2B3C4D5E6F7081", AlreadyCompleteMode.SATISFY, Optional.empty());
        QuestDefinition def = definitionWith(Optional.empty(), List.of(objective), List.of());

        List<FtbqReferenceWalker.Reference> refs = FtbqReferenceWalker.collect(def);
        assertEquals(1, refs.size());
        assertEquals("objectives[0].quest", refs.get(0).field());
        assertEquals(FtbqReferenceWalker.Kind.QUEST, refs.get(0).kind());
        assertEquals("1A2B3C4D5E6F7081", refs.get(0).hexId());
    }

    @Test
    void ftbqProgressRewardCompleteQuestActionIsQuestKind() {
        FtbqProgressReward reward = new FtbqProgressReward(FtbqProgressReward.ProgressAction.COMPLETE_QUEST, "1A2B3C4D5E6F7081");
        QuestDefinition def = definitionWith(Optional.empty(), List.of(), List.of(reward));

        List<FtbqReferenceWalker.Reference> refs = FtbqReferenceWalker.collect(def);
        assertEquals(1, refs.size());
        assertEquals("rewards[0].id", refs.get(0).field());
        assertEquals(FtbqReferenceWalker.Kind.QUEST, refs.get(0).kind());
    }

    @Test
    void ftbqProgressRewardTaskActionsAreTaskKind() {
        FtbqProgressReward completeTask = new FtbqProgressReward(FtbqProgressReward.ProgressAction.COMPLETE_TASK, "F00DF00DF00DF00D");
        FtbqProgressReward resetTask = new FtbqProgressReward(FtbqProgressReward.ProgressAction.RESET_TASK, "F00DF00DF00DF00D");
        QuestDefinition def = definitionWith(Optional.empty(), List.of(), List.of(completeTask, resetTask));

        List<FtbqReferenceWalker.Reference> refs = FtbqReferenceWalker.collect(def);
        assertEquals(2, refs.size());
        assertTrue(refs.stream().allMatch(r -> r.kind() == FtbqReferenceWalker.Kind.TASK));
        assertEquals("rewards[0].id", refs.get(0).field());
        assertEquals("rewards[1].id", refs.get(1).field());
    }
}
