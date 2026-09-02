package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line between "who this person is" and "where this person is standing", which is where a
 * delivery quest stopped registering.
 *
 * <p>Four bundled delivery quests bind {@code "require": "nearby"} — bring mother a meal, bring the
 * child a toy. {@code nearby} is <em>loaded and within twelve blocks of the quest giver</em>, and the
 * credit check used to re-run that whole predicate at the moment the goods changed hands. By then the
 * player has walked from the giver to the recipient, so the predicate is false by construction and
 * the hand-over was refused every time, silently, with the objective left at 0/1.
 *
 * <p>{@link RelativeCandidate#matchesIdentity} is the identity half of the same status, and this is
 * its truth table. Only {@code nearby} may differ from {@link RelativeCandidate#matches}, and it may
 * differ in exactly one way: by no longer asking where the person is standing.
 */
class RelativeCandidateIdentityTest {

    private static final List<String> STATUSES =
            List.of("alive", "reachable", "nearby", "missing", "dead", "same_village", "any_known");

    private static RelativeCandidate candidate(boolean nodeKnown, boolean deceased, boolean generated,
                                               boolean player, boolean embodied, boolean loaded,
                                               boolean nearby, boolean sameVillage,
                                               boolean residentAnywhere) {
        return new RelativeCandidate(UUID.randomUUID(), "parent", "Ada", nodeKnown, deceased, generated,
                player, embodied, loaded, nearby, sameVillage, residentAnywhere, false);
    }

    /** A living parent on the village roll, standing right beside the giver. */
    private static RelativeCandidate parentBesideTheGiver() {
        return candidate(true, false, false, false, true, true, true, true, true);
    }

    /** The same person a minute later: still loaded, still on the roll, no longer next to the giver. */
    private static RelativeCandidate parentWhoWalkedAway() {
        return candidate(true, false, false, false, true, true, false, true, true);
    }

    private static List<String> satisfied(RelativeCandidate candidate) {
        return STATUSES.stream().filter(candidate::matches).toList();
    }

    private static List<String> satisfiedByIdentity(RelativeCandidate candidate) {
        return STATUSES.stream().filter(candidate::matchesIdentity).toList();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("the regression: a parent who walked away from the giver stops being 'nearby'")
    void walkingAwayFromTheGiverBreaksTheSelectionPredicate() {
        assertTrue(parentBesideTheGiver().matches("nearby"));
        assertFalse(parentWhoWalkedAway().matches("nearby"),
                "this is correct for selection, and is exactly why it cannot also be the credit check");
    }

    @Test
    @DisplayName("...but they are still the same findable person, so identity still matches")
    void identitySurvivesTheWalk() {
        assertTrue(parentWhoWalkedAway().matchesIdentity("nearby"));
        assertTrue(parentBesideTheGiver().matchesIdentity("nearby"));
    }

    @Test
    @DisplayName("identity drops the distance test and nothing else")
    void nearbyIsTheOnlyStatusThatDiffers() {
        for (RelativeCandidate subject : List.of(parentBesideTheGiver(), parentWhoWalkedAway())) {
            for (String status : STATUSES) {
                if ("nearby".equals(status)) {
                    continue;
                }
                assertEquals(subject.matches(status), subject.matchesIdentity(status),
                        "status '" + status + "' must answer identically either way");
            }
        }
    }

    /**
     * Identity is not a licence to credit anybody. The personhood half of every status is kept, so the
     * dead, MCA's fabricated ancestors, and players are all still refused — which is the bug the
     * {@code require} filter was introduced to fix in the first place, and it stays fixed.
     */
    @Test
    @DisplayName("a dead relative is still not an acceptable recipient, by identity or otherwise")
    void theDeadAreStillRefused() {
        RelativeCandidate deadButStillOnTheRoll =
                candidate(true, true, false, false, false, false, false, true, true);
        assertFalse(deadButStillOnTheRoll.matchesIdentity("nearby"));
        assertFalse(deadButStillOnTheRoll.matchesIdentity("reachable"));
        assertFalse(deadButStillOnTheRoll.matchesIdentity("same_village"));
        assertEquals(List.of("dead", "any_known"), satisfiedByIdentity(deadButStillOnTheRoll));
    }

    @Test
    @DisplayName("a fabricated ancestor MCA invented is refused by identity too")
    void generatedAncestorsAreStillRefused() {
        RelativeCandidate generated =
                candidate(true, false, true, false, false, false, false, false, false);
        assertEquals(List.of("any_known"), satisfiedByIdentity(generated));
    }

    /**
     * Someone genuinely gone — no body, on no roll — is not made findable by dropping the distance
     * test. An unbound {@code nearby} target must still refuse them, or a quest could be credited by a
     * villager who is not in the world.
     */
    @Test
    @DisplayName("a missing relative is not resurrected by the identity relaxation")
    void theMissingAreStillRefused() {
        RelativeCandidate missing =
                candidate(true, false, false, false, false, false, false, false, false);
        assertFalse(missing.matchesIdentity("nearby"));
        assertEquals(List.of("alive", "missing", "any_known"), satisfiedByIdentity(missing));
    }

    /** An unloaded relative who is on a village roll is reachable, and stays so under identity. */
    @Test
    @DisplayName("an unloaded relative on a village roll is still the person the quest named")
    void anUnloadedButHousedRelativeStillMatches() {
        RelativeCandidate unloaded =
                candidate(true, false, false, false, false, false, false, true, true);
        assertEquals(List.of("alive", "reachable", "same_village", "any_known"), satisfied(unloaded));
        assertTrue(unloaded.matchesIdentity("nearby"));
    }

    @Test
    @DisplayName("an unknown status still fails closed under identity")
    void unknownStatusesFailClosed() {
        assertFalse(parentBesideTheGiver().matchesIdentity("standing_on_one_leg"));
    }
}
