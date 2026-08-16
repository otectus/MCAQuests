package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.SituationManager.TickAction;
import dev.otectus.mcaquests.quest.situation.SituationOutcomes;
import dev.otectus.mcaquests.quest.situation.SituationOutcomes.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
