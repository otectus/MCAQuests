package dev.otectus.mcaquests.quest;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.ObjectiveSupport;
import dev.otectus.mcaquests.quest.objective.ReachLocationObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard that stops escort/reach quests being completed without doing them.
 *
 * <p>Before this, nothing asked whether a quest's objectives were <em>already</em> satisfied.
 * {@code EscortEntityObjective} freezes its destination on the first poll — about a second after
 * accept — and evaluates arrival in the same call, so a villager standing at the destination completed
 * the quest before the player moved. Accept, turn straight back in to the giver standing right there,
 * collect currency, XP and hearts, repeat every cooldown. "Walk me to my bed", offered at night by a
 * villager already at their bed, was the worst case.
 *
 * <p>Two layers close it, and the second is what this file mostly covers: the offer filter (a quest in
 * that state is never offered) and the runtime latch below (arrival is not credited until the subject
 * has genuinely been away from the destination), which is what catches a quest that never passed the
 * offer gate — a chain stage, or one granted by command.
 */
class JourneyGuardTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** Longer than ObjectiveSupport's arming window, so the quest reads as mid-journey. */
    private static final long OLD_QUEST = 5_000L;

    // --- the latch's decision table ---------------------------------------------------------------

    @Test
    void aSubjectStartingAtTheDestinationIsNotArmed() {
        ObjectiveProgress progress = new ObjectiveProgress();

        assertFalse(ObjectiveSupport.isJourneyArmed(progress, 100L, 100L, true),
                "A quest accepted with its subject already at the destination must not credit arrival.");
    }

    @Test
    void aSubjectStartingAwayFromTheDestinationIsArmedImmediately() {
        ObjectiveProgress progress = new ObjectiveProgress();

        assertTrue(ObjectiveSupport.isJourneyArmed(progress, 100L, 100L, false));
    }

    @Test
    void leavingTheDestinationArmsAQuestThatStartedDisarmed() {
        ObjectiveProgress progress = new ObjectiveProgress();
        assertFalse(ObjectiveSupport.isJourneyArmed(progress, 100L, 100L, true));

        // The player drags the villager out of the zone: the journey has genuinely begun.
        assertTrue(ObjectiveSupport.isJourneyArmed(progress, 140L, 100L, false));
    }

    @Test
    void armingIsOneWayOnceTheJourneyHasBegun() {
        ObjectiveProgress progress = new ObjectiveProgress();
        assertFalse(ObjectiveSupport.isJourneyArmed(progress, 100L, 100L, true));
        assertTrue(ObjectiveSupport.isJourneyArmed(progress, 140L, 100L, false));

        // Walking back in must not disarm it — that is the arrival we are here to credit.
        assertTrue(ObjectiveSupport.isJourneyArmed(progress, 200L, 100L, true),
                "Returning to the destination is the completion, not a reason to re-lock the objective.");
    }

    /**
     * The compatibility rule: a quest already in flight when this guard shipped has no latch, and must
     * be treated as armed. Deciding it from the subject's current position would strand a player who was
     * part-way through an escort — or standing at the destination — at the moment they updated.
     */
    @Test
    void anInFlightQuestFromBeforeTheGuardIsTreatedAsArmed() {
        ObjectiveProgress progress = new ObjectiveProgress();

        assertTrue(ObjectiveSupport.isJourneyArmed(progress, OLD_QUEST, 0L, true),
                "A quest older than the arming window predates the guard and must complete as it always would.");
    }

    @Test
    void theLatchIsPersistedOnFirstEvaluationSoItIsDecidedExactlyOnce() {
        ObjectiveProgress fresh = new ObjectiveProgress();
        ObjectiveSupport.isJourneyArmed(fresh, 100L, 100L, true);
        assertTrue(fresh.extra().contains("startedAway"));
        assertFalse(fresh.extra().getBoolean("startedAway"));

        // Re-evaluating much later must reuse the stored decision, not re-derive it from the clock.
        assertFalse(ObjectiveSupport.isJourneyArmed(fresh, OLD_QUEST, 100L, true),
                "A quest that started at its destination stays disarmed however long it sits there.");
    }

    // --- min_journey codec ------------------------------------------------------------------------

    @Test
    void escortMinJourneyRoundTripsAndDefaultsToAbsent() {
        String json = """
                {
                  "villager": { "mode": "self" },
                  "destination": { "anchor": "bed" },
                  "radius": 4,
                  "min_journey": 32,
                  "lead": true
                }""";

        EscortEntityObjective parsed = EscortEntityObjective.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(error -> new AssertionError("escort_entity failed to parse: " + error));

        assertEquals(Optional.of(32), parsed.minJourney());
        assertEquals(4, parsed.radius());
    }

    @Test
    void escortWithoutMinJourneyLeavesItToTheConfiguredDefault() {
        String json = """
                { "villager": { "mode": "self" }, "destination": { "anchor": "bed" }, "radius": 4 }""";

        EscortEntityObjective parsed = EscortEntityObjective.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(error -> new AssertionError("escort_entity failed to parse: " + error));

        assertEquals(Optional.empty(), parsed.minJourney(),
                "An absent min_journey must stay absent so the config default applies at runtime.");
    }

    @Test
    void reachLocationAcceptsMinJourney() {
        String json = """
                { "location": { "anchor": "home_village" }, "radius": 8, "min_journey": 64 }""";

        ReachLocationObjective parsed = ReachLocationObjective.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(error -> new AssertionError("reach_location failed to parse: " + error));

        assertEquals(Optional.of(64), parsed.minJourney());
        assertEquals(8, parsed.radius());
    }

    /** The default {@code false} is what keeps the new extension point safe for add-on objectives. */
    @Test
    void objectivesThatDoNotOptInAreNeverWithheldFromOffers() {
        String json = """
                { "location": { "anchor": "coords", "pos": [0, 64, 0] } }""";

        ReachLocationObjective reach = ReachLocationObjective.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(error -> new AssertionError("reach_location failed to parse: " + error));

        assertEquals(Optional.empty(), reach.minJourney());
    }
}
