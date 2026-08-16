package dev.otectus.mcaquests;

import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqChapterCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqQuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqTaskCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqWhenMissing;
import dev.otectus.mcaquests.quest.objective.AlreadyCompleteMode;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.reward.FtbqProgressReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §31.5 sabotage harness: install a bridge whose <em>every</em> method throws — simulating an
 * {@code FtbqBridgeImpl} whose internal fail-safe contract has been violated (e.g. by FTB Quests
 * internals drift the per-call guards didn't anticipate) — and prove that no always-loaded caller of
 * the {@link FtbqBridge.Holder} seam lets the Throwable escape. Together with
 * {@code FtbqConditionPolicyTest} (per-condition {@code when_missing} truth table, including its own
 * throwing stub) this covers every {@code Holder.get()} call site outside {@code compat.ftbq} except
 * the {@code /mcaquests ftbq} command halves and the {@code /mcaquests reload} recheck loop, which
 * need a live {@code CommandSourceStack}/server: those sites are deliberately unguarded because
 * Brigadier's dispatcher contains command exceptions (an error chat line, never a crash) and queued
 * server tasks are contained by {@code BlockableEventLoop.doRunTask}'s catch — exercised in-world by
 * matrix #7 instead.
 *
 * <p>{@code Holder} is JVM-global, so the previous bridge is restored in {@code @AfterEach} exactly
 * as {@code FtbqConditionPolicyTest} does.
 */
class FtbqBridgeSabotageTest {

    private static final String HEX = "1A2B3C4D5E6F7081";
    private static final QuestContext CONTEXT =
            new QuestContext(null, null, null, ResourceLocation.fromNamespaceAndPath("mcaquests", "dummy"), null);

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /**
     * Loads the common config spec against an in-memory night-config so
     * {@code McaQuestsConfig.COMMON.allowFtbqProgressRewards.get()} — read by
     * {@link FtbqProgressReward#grant} <em>before</em> its Throwable guard — returns its default
     * ({@code true}) instead of throwing "config not loaded". JVM-global like the vanilla bootstrap
     * flip, and equally harmless: no other test in the suite reads live config values.
     */
    @BeforeAll
    static void loadCommonConfigDefaults() {
        dev.otectus.mcaquests.support.TestConfigs.loadDefaults(McaQuestsConfig.COMMON_SPEC);
    }

    /** Every method throws, including the interface's {@code default} methods. */
    private static final class SabotagedBridge implements FtbqBridge {
        private static RuntimeException boom() {
            return new IllegalStateException("sabotaged bridge (§31.5 harness)");
        }

        @Override public boolean isAvailable() { throw boom(); }
        @Override public boolean isQuestCompleted(ServerPlayer player, String hexId) { throw boom(); }
        @Override public boolean isChapterCompleted(ServerPlayer player, String hexId) { throw boom(); }
        @Override public boolean isTaskCompleted(ServerPlayer player, String hexId) { throw boom(); }
        @Override public boolean questIdExists(String hexId) { throw boom(); }
        @Override public boolean chapterIdExists(String hexId) { throw boom(); }
        @Override public boolean taskIdExists(String hexId) { throw boom(); }
        @Override public boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId) { throw boom(); }
        @Override public void recheckAll(ServerPlayer player) { throw boom(); }
        @Override public int[] integrationObjectCounts() { throw boom(); }
        @Override public boolean isReal() { throw boom(); }
        @Override public List<BookReference> validateBookReferences() { throw boom(); }
    }

    private FtbqBridge previous;

    @BeforeEach
    void sabotageBridge() {
        previous = FtbqBridge.Holder.get();
        FtbqBridge.Holder.set(new SabotagedBridge());
    }

    @AfterEach
    void restoreBridge() {
        FtbqBridge.Holder.set(previous);
    }

    @Test
    void conditionsFallBackToWhenMissing() {
        List<QuestCondition> notMet = List.of(
                new FtbqQuestCompletedCondition(HEX, FtbqWhenMissing.NOT_MET),
                new FtbqChapterCompletedCondition(HEX, FtbqWhenMissing.NOT_MET),
                new FtbqTaskCompletedCondition(HEX, FtbqWhenMissing.NOT_MET));
        List<QuestCondition> met = List.of(
                new FtbqQuestCompletedCondition(HEX, FtbqWhenMissing.MET),
                new FtbqChapterCompletedCondition(HEX, FtbqWhenMissing.MET),
                new FtbqTaskCompletedCondition(HEX, FtbqWhenMissing.MET));

        for (QuestCondition condition : notMet) {
            assertFalse(assertDoesNotThrow(() -> condition.test(CONTEXT)), "sabotaged + not_met -> false");
        }
        for (QuestCondition condition : met) {
            assertTrue(assertDoesNotThrow(() -> condition.test(CONTEXT)), "sabotaged + met -> true");
        }
    }

    @Test
    void objectivePollReturnsFalseAndLeavesProgressUntouched() {
        FtbqCompleteQuestObjective objective = new FtbqCompleteQuestObjective(
                HEX, AlreadyCompleteMode.SATISFY, Optional.empty());
        ObjectiveProgress progress = new ObjectiveProgress();

        boolean advanced = assertDoesNotThrow(() -> objective.poll(null, null, progress));

        assertFalse(advanced, "sabotaged bridge must read as 'not completed yet'");
        assertEquals(0, progress.count(), "progress must not move on a failed poll");
    }

    @Test
    void objectivePollStaysLatchedWithoutConsultingBridge() {
        FtbqCompleteQuestObjective objective = new FtbqCompleteQuestObjective(
                HEX, AlreadyCompleteMode.SATISFY, Optional.empty());
        ObjectiveProgress progress = new ObjectiveProgress(1);

        boolean advanced = assertDoesNotThrow(() -> objective.poll(null, null, progress));

        assertFalse(advanced, "latched objective reports no change");
        assertEquals(1, progress.count(), "latched progress must survive a sabotaged bridge");
        assertTrue(objective.isSatisfied(null, progress), "latched objective stays satisfied");
    }

    @Test
    void progressRewardGrantSwallowsBridgeFailure() {
        FtbqProgressReward reward =
                new FtbqProgressReward(FtbqProgressReward.ProgressAction.COMPLETE_TASK, HEX);

        assertDoesNotThrow(() -> reward.grant(null, null),
                "a sabotaged grantProgress must be contained by the reward's guard");
    }
}
