package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status vocabulary a quest gates on and a target selects with, as a truth table.
 *
 * <p>This is the direct regression test for the reported bug: a player was asked to deliver a letter to a
 * sibling who had died, because {@code same_village} was satisfied by the village resident roll alone and
 * <b>MCA never takes the dead off that roll</b> — {@code Village.residentNames} is only ever pruned when a
 * villager changes village or an admin command removes them, never on death. The
 * {@link #deadSiblingStillOnTheVillageRollIsNotSameVillage()} row is that exact case.
 *
 * <p>Built from hand-written {@link RelativeCandidate} rows rather than a live family tree on purpose: MCA
 * is excluded from {@code testRuntimeClasspath} (that exclusion is what lets {@code McaCompatSafeFailTest}
 * exercise the real degradation path), so the only predicate that can be tested here is one that does not
 * touch MCA. That is why the status switch lives on the candidate and not inside the reflective walk.
 */
class RelativeCandidateFilterTest {

    /** A living, loaded, same-village sibling standing next to the giver — the happy path. */
    private static RelativeCandidate nearbySibling() {
        return candidate("sibling", true, false, false, false, true, true, true, true, true, false);
    }

    private static RelativeCandidate candidate(String relation, boolean nodeKnown, boolean deceased,
                                               boolean generated, boolean player, boolean embodied,
                                               boolean loaded, boolean nearby, boolean sameVillage,
                                               boolean residentAnywhere, boolean materialisable) {
        return new RelativeCandidate(UUID.randomUUID(), relation, "Ada", nodeKnown, deceased, generated,
                player, embodied, loaded, nearby, sameVillage, residentAnywhere, materialisable);
    }

    /** Which of the seven statuses {@code candidate} satisfies, in a stable order for readable failures. */
    private static List<String> satisfied(RelativeCandidate candidate) {
        return List.of("alive", "reachable", "nearby", "missing", "dead", "same_village", "any_known")
                .stream().filter(candidate::matches).toList();
    }

    @Test
    @DisplayName("a living, loaded, same-village relative satisfies every existence status")
    void livingLoadedRelativeSatisfiesTheExistenceStatuses() {
        assertEquals(List.of("alive", "reachable", "nearby", "same_village", "any_known"),
                satisfied(nearbySibling()));
    }

    @Test
    @DisplayName("the reported bug: a dead sibling still on the village roll is NOT same_village")
    void deadSiblingStillOnTheVillageRollIsNotSameVillage() {
        // Exactly what MCA leaves behind: the family node is flagged deceased, the body is gone, and the
        // village roll still lists them because MCA only prunes it on a move or an admin command.
        RelativeCandidate deadButOnTheRoll =
                candidate("sibling", true, true, false, false, false, false, false, true, true, false);

        assertFalse(deadButOnTheRoll.matches("same_village"),
                "a deceased relative on a stale village roll must not satisfy same_village — this is the "
                        + "gate that offered 'deliver a letter to your brother' about someone who had died");
        assertFalse(deadButOnTheRoll.matches("alive"));
        assertFalse(deadButOnTheRoll.matches("reachable"));
        assertFalse(deadButOnTheRoll.matches("missing"), "dead is not missing");
        assertTrue(deadButOnTheRoll.matches("dead"));
        assertTrue(deadButOnTheRoll.matches("any_known"), "any_known is the opt-in loose behaviour");
    }

    @Test
    @DisplayName("a fabricated ancestor is neither findable nor a bereavement")
    void probablyGeneratedAncestorSatisfiesOnlyAnyKnown() {
        // MCA gives every naturally spawned villager two invented, deceased parents. Before this, they
        // were the first entries in a relation:"any" walk, so almost every "any" quest could bind one.
        RelativeCandidate inventedParent =
                candidate("parent", true, true, true, false, false, false, false, false, false, false);

        assertEquals(List.of("any_known"), satisfied(inventedParent));
        assertFalse(inventedParent.matches("dead"),
                "a villager MCA invented to pad a family tree was never alive, so mourning them is not a thing");
    }

    @Test
    @DisplayName("a player node is never a villager a quest may be about")
    void playerNodeSatisfiesOnlyAnyKnown() {
        RelativeCandidate playerChild =
                candidate("child", true, false, false, true, true, true, true, true, true, false);
        assertEquals(List.of("any_known"), satisfied(playerChild));
    }

    @Test
    @DisplayName("an unloaded relative on a village roll is reachable but not nearby and not missing")
    void unloadedButRostered() {
        RelativeCandidate awayFromHome =
                candidate("child", true, false, false, false, false, false, false, true, true, true);

        assertTrue(awayFromHome.matches("alive"));
        assertTrue(awayFromHome.matches("reachable"), "a roll says where to find them");
        assertTrue(awayFromHome.matches("same_village"));
        assertFalse(awayFromHome.matches("nearby"));
        assertFalse(awayFromHome.matches("missing"),
                "merely out of render distance is not missing — materialising them would duplicate a "
                        + "villager who is alive and well");
    }

    @Test
    @DisplayName("no body and no village roll is missing, and missing is not reachable")
    void genuinelyVanished() {
        RelativeCandidate vanished =
                candidate("child", true, false, false, false, false, false, false, false, false, true);

        assertEquals(List.of("alive", "missing", "any_known"), satisfied(vanished));
        assertFalse(vanished.matches("reachable"),
                "nothing but find_missing_relative materialises anyone, so 'go and give this to them' "
                        + "would never finish");
        assertTrue(vanished.materialisable(), "which is what makes a missing-kin quest possible at all");
    }

    @Test
    @DisplayName("an unknown node satisfies nothing at all")
    void unknownNodeFailsClosed() {
        RelativeCandidate unknown =
                candidate("sibling", false, false, false, false, false, false, false, false, false, false);
        assertEquals(List.of(), satisfied(unknown));
    }

    @Test
    @DisplayName("a body that is present but not alive counts as neither loaded nor missing")
    void embodiedButDying() {
        // getEntity finds them, so materialising would duplicate; but they are not a target to walk to.
        RelativeCandidate dying =
                candidate("sibling", true, false, false, false, true, false, false, false, false, false);

        assertTrue(dying.matches("alive"), "the family tree has not flagged them deceased yet");
        assertFalse(dying.matches("reachable"));
        assertFalse(dying.matches("missing"), "there is a body, so they have not vanished");
    }

    @Test
    @DisplayName("an unknown status name fails closed rather than matching everything")
    void unknownStatusFailsClosed() {
        assertFalse(nearbySibling().matches("estranged"));
        assertFalse(nearbySibling().matches(""));
    }

    @Test
    @DisplayName("the declared vocabulary is exactly what matches() answers")
    void vocabularyAndSwitchAgree() {
        RelativeCandidate everything = nearbySibling();
        for (String status : RelativeCandidate.STATUSES) {
            // Not asserting the value — asserting that every declared status is a case the switch knows.
            // A status added to the set and forgotten in the switch would silently gate nothing.
            everything.matches(status);
        }
        assertTrue(RelativeCandidate.STATUSES.containsAll(RelativeCandidate.EXISTENCE_STATUSES),
                "the existence subset must name statuses that actually exist");
        assertTrue(RelativeCandidate.STATUSES.contains(RelativeCandidate.DEFAULT_FAMILY_REQUIRE));
        for (String status : RelativeCandidate.EXISTENCE_STATUSES) {
            assertFalse(candidate("sibling", true, true, false, false, false, false, false, true, true, false)
                            .matches(status),
                    "a deceased relative must fail every status that claims they can be found: " + status);
        }
    }
}
