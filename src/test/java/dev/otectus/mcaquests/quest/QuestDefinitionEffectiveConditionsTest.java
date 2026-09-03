package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqQuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqWhenMissing;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.objective.AlreadyCompleteMode;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link QuestDefinition#effectiveConditions()}'s {@code block_offer} desugar
 * (spec §18): a quest with an {@code ftbq_complete_quest} objective whose {@code already_complete} is
 * {@code block_offer} must have its offer gate auto-wrapped with {@code not(ftbq_quest_completed)},
 * exactly mirroring the pre-existing chain-prerequisite desugar in the same method. {@code satisfy}
 * mode must leave the gate untouched.
 */
class QuestDefinitionEffectiveConditionsTest {

    private static final String HEX = "1A2B3C4D5E6F7081";
    private static final ResourceLocation QUEST_ID = ResourceLocation.fromNamespaceAndPath("mcaquests", "archivist_bonus");

    static {
        // See QuestFilterTest for why real QuestDefinitions need the vanilla "bootstrapped" flag flipped.
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestDefinition definitionWith(Optional<QuestCondition> conditions, Optional<ChainSpec> chain,
                                                   QuestObjective... objectives) {
        return new QuestDefinition(QUEST_ID, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                GiverSpec.ANY, Map.of(), List.of(objectives), List.of(), TurnInSpec.DEFAULT, conditions, chain,
                Optional.empty(), Optional.empty(), OfferShaping.NONE, dev.otectus.mcaquests.quest.reputation.QuestReputationBlock.NONE);
    }

    private static FtbqCompleteQuestObjective ftbqObjective(AlreadyCompleteMode mode) {
        return new FtbqCompleteQuestObjective(HEX, mode, Optional.empty());
    }

    // --- satisfy: no desugar -------------------------------------------------------------------

    @Test
    void satisfyModeWithNoAuthorConditionsLeavesEffectiveConditionsEmpty() {
        QuestDefinition def = definitionWith(Optional.empty(), Optional.empty(), ftbqObjective(AlreadyCompleteMode.SATISFY));
        assertTrue(def.effectiveConditions().isEmpty(),
                "satisfy mode must not desugar anything into effectiveConditions()");
    }

    @Test
    void satisfyModeWithAuthorConditionsLeavesThemUnchanged() {
        QuestCondition author = new QuestCompletedCondition(ResourceLocation.fromNamespaceAndPath("mcaquests", "some_other_quest"),
                HistoryScope.GLOBAL);
        QuestDefinition def = definitionWith(Optional.of(author), Optional.empty(),
                ftbqObjective(AlreadyCompleteMode.SATISFY));
        assertEquals(Optional.of(author), def.effectiveConditions(),
                "satisfy mode must pass the author's own conditions through unchanged");
    }

    // --- block_offer: desugar -------------------------------------------------------------------

    @Test
    void blockOfferWithNoAuthorConditionsWrapsExactlyTheNotFtbqCondition() {
        QuestDefinition def = definitionWith(Optional.empty(), Optional.empty(),
                ftbqObjective(AlreadyCompleteMode.BLOCK_OFFER));
        Optional<QuestCondition> effective = def.effectiveConditions();
        assertTrue(effective.isPresent());
        assertTrue(effective.get() instanceof AllOfCondition, "block_offer must wrap in an AllOfCondition");
        List<QuestCondition> children = ((AllOfCondition) effective.get()).conditions();
        assertEquals(1, children.size());
        assertNotFtbqQuestCompleted(children.get(0), HEX);
    }

    @Test
    void blockOfferComposesWithAuthorConditionsAndChainPrerequisite() {
        QuestCondition author = new QuestCompletedCondition(ResourceLocation.fromNamespaceAndPath("mcaquests", "some_other_quest"),
                HistoryScope.GLOBAL);
        ResourceLocation prerequisiteId = ResourceLocation.fromNamespaceAndPath("mcaquests", "prereq_quest");
        ChainSpec chain = new ChainSpec("family_arc", 2, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(prerequisiteId), List.of());
        QuestDefinition def = definitionWith(Optional.of(author), Optional.of(chain),
                ftbqObjective(AlreadyCompleteMode.BLOCK_OFFER));

        Optional<QuestCondition> effective = def.effectiveConditions();
        assertTrue(effective.isPresent());
        List<QuestCondition> children = ((AllOfCondition) effective.get()).conditions();
        assertEquals(3, children.size(), "author condition + chain prerequisite + ftbq not-wrap");
        assertTrue(children.contains(author));
        assertTrue(children.stream().anyMatch(c -> c instanceof QuestCompletedCondition qc
                && qc.quest().equals(prerequisiteId) && qc.scope() == HistoryScope.GIVER));
        assertTrue(children.stream().anyMatch(c -> isNotFtbqQuestCompleted(c, HEX)));
    }

    @Test
    void satisfyModeObjectiveDoesNotTriggerBlockOfferDesugarEvenAlongsideChainPrerequisite() {
        ResourceLocation prerequisiteId = ResourceLocation.fromNamespaceAndPath("mcaquests", "prereq_quest");
        ChainSpec chain = new ChainSpec("family_arc", 2, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(prerequisiteId), List.of());
        QuestDefinition def = definitionWith(Optional.empty(), Optional.of(chain),
                ftbqObjective(AlreadyCompleteMode.SATISFY));

        Optional<QuestCondition> effective = def.effectiveConditions();
        assertTrue(effective.isPresent());
        List<QuestCondition> children = ((AllOfCondition) effective.get()).conditions();
        assertEquals(1, children.size(), "only the chain prerequisite — no ftbq not-wrap for satisfy mode");
        assertFalse(children.stream().anyMatch(c -> isNotFtbqQuestCompleted(c, HEX)));
    }

    // --- functional: the desugared condition actually gates on the bridge's real answer ----------

    private FtbqBridge previous;

    @BeforeEach
    void captureExistingBridge() {
        previous = FtbqBridge.Holder.get();
    }

    @AfterEach
    void restoreExistingBridge() {
        FtbqBridge.Holder.set(previous);
    }

    private static final class StubBridge implements FtbqBridge {
        boolean completed;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isQuestCompleted(ServerPlayer player, String hexId) {
            return completed;
        }

        @Override
        public boolean isChapterCompleted(ServerPlayer player, String hexId) {
            return false;
        }

        @Override
        public boolean isTaskCompleted(ServerPlayer player, String hexId) {
            return false;
        }

        @Override
        public boolean questIdExists(String hexId) {
            return true;
        }

        @Override
        public boolean chapterIdExists(String hexId) {
            return false;
        }

        @Override
        public boolean taskIdExists(String hexId) {
            return false;
        }

        @Override
        public boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId) {
            return false;
        }

        @Override
        public void recheckAll(ServerPlayer player) {
        }

        @Override
        public int[] integrationObjectCounts() {
            return new int[]{0, 0};
        }
    }

    @Test
    void blockOfferGateHidesOfferOnceFtbQuestIsPositivelyCompleted() {
        StubBridge stub = new StubBridge();
        FtbqBridge.Holder.set(stub);
        QuestDefinition def = definitionWith(Optional.empty(), Optional.empty(),
                ftbqObjective(AlreadyCompleteMode.BLOCK_OFFER));
        QuestContext context = new QuestContext(null, null, null, QUEST_ID, null);

        stub.completed = false;
        assertTrue(def.effectiveConditions().get().test(context), "not yet completed -> offer stays visible");

        stub.completed = true;
        assertFalse(def.effectiveConditions().get().test(context), "completed -> offer hidden");
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static void assertNotFtbqQuestCompleted(QuestCondition condition, String expectedHex) {
        assertTrue(isNotFtbqQuestCompleted(condition, expectedHex),
                "expected a not(ftbq_quest_completed(" + expectedHex + ")) condition, got: " + condition);
    }

    private static boolean isNotFtbqQuestCompleted(QuestCondition condition, String expectedHex) {
        return condition instanceof NotCondition not
                && not.condition() instanceof FtbqQuestCompletedCondition ftbq
                && ftbq.quest().equals(expectedHex)
                && ftbq.whenMissing() == FtbqWhenMissing.NOT_MET;
    }
}
