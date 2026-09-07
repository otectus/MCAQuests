package dev.otectus.mcaquests;

import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.SituationManager.TickAction;
import dev.otectus.mcaquests.quest.situation.SituationOutcomes;
import dev.otectus.mcaquests.quest.situation.SituationOutcomes.Outcome;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.quest.situation.state.SituationStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for the situation resolution tick state machine and outcome defaults (0.8.0). */
class SituationResolutionTest {

    @Test
    void closedSituationsAreNeverActedOn() {
        assertEquals(TickAction.NONE, SituationManager.tickDecision(false, true, true));
        assertEquals(TickAction.NONE, SituationManager.tickDecision(false, false, false));
    }

    @Test
    void expiryTakesPrecedenceOverClearance() {
        assertEquals(TickAction.EXPIRE, SituationManager.tickDecision(true, true, false));
        assertEquals(TickAction.EXPIRE, SituationManager.tickDecision(true, true, true));
    }

    @Test
    void clearedWhenConditionLiftedBeforeDeadline() {
        assertEquals(TickAction.CLEAR, SituationManager.tickDecision(true, false, true));
    }

    @Test
    void stillRunningWhenNeitherExpiredNorCleared() {
        assertEquals(TickAction.NONE, SituationManager.tickDecision(true, false, false));
    }

    @Test
    void outcomesDefaultToNoChange() {
        assertEquals(Outcome.NONE, SituationOutcomes.NONE.success());
        assertEquals(Outcome.NONE, SituationOutcomes.NONE.failure());
        assertEquals(Outcome.NONE, SituationOutcomes.NONE.cleared());
        assertEquals(0, Outcome.NONE.reputation());
        assertEquals(0, Outcome.NONE.hearts());
    }

    @Test
    void outcomeBranchesCarryTheirDeltas() {
        SituationOutcomes outcomes = new SituationOutcomes(
                new Outcome(10, 2), new Outcome(-5, 0), Outcome.NONE);
        assertEquals(10, outcomes.success().reputation());
        assertEquals(2, outcomes.success().hearts());
        assertEquals(-5, outcomes.failure().reputation());
        assertEquals(Outcome.NONE, outcomes.cleared());
    }

    private static SituationInstance capitalSituation() {
        return new SituationInstance(UUID.randomUUID(),
                new ResourceLocation("mcaquests", "capitals_the_empty_throne"), 12,
                null, null, 100L, 1100L, 0L, SituationStatus.OPEN);
    }

    @Test
    void acceptedSituationPausesAcrossRestartAndRetainsRemainingTimeOnRecovery() {
        SituationSavedData data = new SituationSavedData();
        SituationInstance instance = capitalSituation();
        instance.addParticipant(UUID.randomUUID());
        data.putInstance(instance);
        data.setDirty(false);

        assertTrue(SituationManager.pauseUnavailableSituation(data, instance, 300L, true));
        assertTrue(data.isDirty());
        assertEquals(800L, instance.remainingTicks(5000L));
        assertFalse(instance.isExpiredAt(5000L));

        SituationInstance restored = SituationInstance.load(instance.save());
        assertTrue(SituationManager.pauseUnavailableSituation(data, restored, 5000L, true));
        assertEquals(800L, restored.remainingTicks(5000L));
        assertFalse(SituationManager.pauseUnavailableSituation(data, restored, 6000L, false));
        assertEquals(5700L, restored.suspendedTicks(6000L));
        assertEquals(6800L, restored.deadlineGameTime());
        assertFalse(restored.isExpiredAt(6799L));
        assertTrue(restored.isExpiredAt(6800L));
        SituationInstance resumedReload = SituationInstance.load(restored.save());
        assertEquals(6800L, resumedReload.deadlineGameTime(), "completed pauses must not be added twice on reload");
        assertEquals(800L, resumedReload.remainingTicks(6000L));
    }

    @Test
    void unacceptedSituationsStillExpireDuringAnOutage() {
        SituationInstance instance = capitalSituation();
        assertFalse(SituationManager.pauseUnavailableSituation(new SituationSavedData(), instance, 300L, true));
        assertTrue(instance.isExpiredAt(1100L));
        assertEquals(0L, instance.suspendedTicks(1100L));
    }

    @Test
    void playableAcceptedSituationsKeepTheirOriginalDeadline() {
        SituationInstance instance = capitalSituation();
        instance.addParticipant(UUID.randomUUID());
        assertFalse(SituationManager.pauseUnavailableSituation(new SituationSavedData(), instance, 300L, false));
        assertTrue(instance.isExpiredAt(1100L));
        assertEquals(1100L, instance.deadlineGameTime());
    }

    @Test
    void oldSituationSavesLoadWithAnUnpausedClock() {
        SituationInstance original = capitalSituation();
        SituationInstance restored = SituationInstance.load(original.save());
        assertEquals(0L, restored.suspendedTicks(900L));
        assertEquals(200L, restored.remainingTicks(900L));
        assertTrue(restored.isExpiredAt(1100L));
    }

    @Test
    void multipleOutagesAccumulateWithoutCountingPlayableTime() {
        SituationInstance instance = capitalSituation();
        instance.addParticipant(UUID.randomUUID());
        SituationSavedData data = new SituationSavedData();
        SituationManager.pauseUnavailableSituation(data, instance, 200L, true);
        SituationManager.pauseUnavailableSituation(data, instance, 500L, false);
        SituationManager.pauseUnavailableSituation(data, instance, 700L, true);
        SituationManager.pauseUnavailableSituation(data, instance, 900L, false);

        assertEquals(500L, instance.suspendedTicks(900L));
        assertEquals(700L, instance.remainingTicks(900L));
        // Newly accepted copies retain the original anchor and receive the master's pause credit.
        assertEquals(instance.deadlineGameTime(), instance.openGameTime() + instance.suspendedTicks(900L) + 1000L);
    }

    @Test
    void offlineCopyReceivesSharedPauseOnceAndOnlineCopyDoesNotDoubleAccrue() {
        SituationInstance instance = capitalSituation();
        instance.updateSuspension(200L, true);
        instance.updateSuspension(700L, false);
        assertEquals(500L, instance.missingSuspendedTicks(0L, 900L));
        assertEquals(0L, instance.missingSuspendedTicks(500L, 900L));

        instance.updateSuspension(1000L, true);
        assertEquals(20L, instance.missingSuspendedTicks(500L, 1020L));
        assertEquals(0L, instance.missingSuspendedTicks(520L, 1020L),
                "a second player sweep in the same tick must not add another pause interval");
    }

    @Test
    void lateJoinedCopyDoesNotReceiveItsSeededPauseCreditTwice() {
        SituationInstance instance = capitalSituation();
        instance.updateSuspension(200L, true);
        instance.updateSuspension(700L, false);
        long seededPause = instance.suspendedTicks(800L);
        assertEquals(0L, instance.missingSuspendedTicks(seededPause, 800L));

        instance.updateSuspension(900L, true);
        instance.updateSuspension(1100L, false);
        assertEquals(200L, instance.missingSuspendedTicks(seededPause, 1200L));
        assertEquals(0L, instance.missingSuspendedTicks(seededPause + 200L, 1200L));
    }

    @Test
    void resolvedTemplateRequirementsSurviveOfflineSaveAndReload() {
        SituationInstance instance = capitalSituation();
        UUID player = UUID.randomUUID();
        instance.addParticipant(player);
        instance.setParticipantRequirements(player, Set.of(CapitalsCapability.REGISTRY, CapitalsCapability.ROLES));
        SituationInstance restored = SituationInstance.load(instance.save());

        assertTrue(restored.hasActiveParticipants());
        assertEquals(Set.of(CapitalsCapability.REGISTRY, CapitalsCapability.ROLES),
                restored.participantRequirements(player));
        assertTrue(restored.needsUnavailableCapability(cap -> cap != CapitalsCapability.ROLES));
        assertTrue(SituationManager.pauseUnavailableSituation(new SituationSavedData(), restored, 200L,
                restored.needsUnavailableCapability(cap -> cap != CapitalsCapability.ROLES)));
        assertFalse(restored.isExpiredAt(2000L));
    }

    @Test
    void finalAbandonReleasesPauseWithoutRemovingHistoricalOutcomeParticipants() {
        SituationInstance instance = capitalSituation();
        UUID player = UUID.randomUUID();
        instance.addParticipant(player);
        instance.setParticipantRequirements(player, Set.of(CapitalsCapability.REGISTRY));
        SituationSavedData data = new SituationSavedData();
        assertTrue(SituationManager.pauseUnavailableSituation(data, instance, 200L, true));

        instance.removeActiveParticipant(player);
        assertFalse(SituationManager.pauseUnavailableSituation(data, instance, 400L, true));
        SituationInstance restored = SituationInstance.load(instance.save());
        assertEquals(Set.of(player), restored.participants(), "history remains available for outcomes");
        assertFalse(restored.hasActiveParticipants(), "an empty persisted active map must not fall back to history");
        assertTrue(restored.isExpiredAt(1300L), "the shared slot can expire normally after the last abandonment");
    }

    @Test
    void releasingOneParticipantKeepsTheOtherOfflineParticipantsRequirements() {
        SituationInstance instance = capitalSituation();
        UUID departed = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        instance.addParticipant(departed);
        instance.addParticipant(offline);
        instance.setParticipantRequirements(departed, Set.of(CapitalsCapability.CHRONICLE));
        instance.setParticipantRequirements(offline, Set.of(CapitalsCapability.ROLES));
        instance.removeActiveParticipant(departed);
        SituationInstance restored = SituationInstance.load(instance.save());
        assertTrue(restored.hasActiveParticipants());
        assertFalse(restored.needsUnavailableCapability(cap -> cap != CapitalsCapability.CHRONICLE));
        assertTrue(restored.needsUnavailableCapability(cap -> cap != CapitalsCapability.ROLES));
        assertEquals(Set.of(departed, offline), restored.participants());
    }
}
