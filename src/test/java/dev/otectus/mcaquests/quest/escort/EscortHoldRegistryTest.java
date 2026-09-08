package dev.otectus.mcaquests.quest.escort;

import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An escort hold outlives the quest that placed it, so something has to remember it.
 *
 * <p>Two failures meet here. A held escortee is {@code noAi} and invulnerable, and the release used to
 * hang off the quest <em>definition</em>: abandon a quest whose definition a datapack reload had
 * removed and the villager stayed frozen for good. And the cleanup, when the escortee was not loaded,
 * fell back to an unbound selector — so it released, un-led and un-followed a completely different
 * villager while the real escortee stayed exactly as it was.
 *
 * <p>The registry answers "who is this player holding" without a definition, and queues a release for
 * anyone it cannot reach. The binding rule is the other half: once an escortee is locked, nobody else
 * is ever the answer.
 */
class EscortHoldRegistryTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private final UUID player = UUID.randomUUID();
    private final UUID otherPlayer = UUID.randomUUID();
    private final UUID escortee = UUID.randomUUID();

    @BeforeEach
    void reset() {
        EscortHoldRegistry.clear();
    }

    @Test
    @DisplayName("a locked escortee is never re-picked by the selector")
    void aBoundEscortNeverAsksAgain() {
        assertTrue(QuestManager.escorteeSelectorAllowed(null),
                "an escort that never bound anybody may still choose");
        assertFalse(QuestManager.escorteeSelectorAllowed(escortee),
                "a bound escort must resolve to that villager or to nobody — never to a substitute");
    }

    @Test
    @DisplayName("holds are recorded per owner and released again")
    void holdsAreBookkept() {
        UUID theirs = UUID.randomUUID();
        EscortHoldRegistry.hold(escortee, player);
        EscortHoldRegistry.hold(theirs, otherPlayer);

        assertEquals(Set.of(escortee), EscortHoldRegistry.heldBy(player));
        assertEquals(Set.of(theirs), EscortHoldRegistry.heldBy(otherPlayer));

        EscortHoldRegistry.release(escortee);
        assertEquals(Set.of(), EscortHoldRegistry.heldBy(player),
                "a released villager is nobody's hold any more");
        assertFalse(EscortHoldRegistry.isReleasePending(escortee),
                "a release given for real leaves nothing owed");
    }

    @Test
    @DisplayName("a hold on an unreachable villager becomes a release owed exactly once")
    void unloadedHoldsBecomeAPendingRelease() {
        EscortHoldRegistry.hold(escortee, player);
        EscortHoldRegistry.enqueueRelease(escortee, player);

        assertEquals(Set.of(), EscortHoldRegistry.heldBy(player),
                "the hold is handed over to the queue, not counted twice");
        assertTrue(EscortHoldRegistry.isReleasePending(escortee));

        Optional<UUID> owner = EscortHoldRegistry.claimRelease(escortee);
        assertEquals(Optional.of(player), owner,
                "the owner comes back with the release, so follow can be reset for the right player");
        assertTrue(EscortHoldRegistry.claimRelease(escortee).isEmpty(),
                "the join handler must not release the same villager twice");
    }

    @Test
    @DisplayName("releasing a queued villager for real clears what was owed")
    void aRealReleaseClearsTheQueue() {
        EscortHoldRegistry.enqueueRelease(escortee, player);
        EscortHoldRegistry.release(escortee);

        assertFalse(EscortHoldRegistry.isReleasePending(escortee));
    }

    @Test
    @DisplayName("shutdown drops every hold, because the entities are about to stop existing")
    void clearDropsEverything() {
        EscortHoldRegistry.hold(escortee, player);
        EscortHoldRegistry.enqueueRelease(UUID.randomUUID(), player);

        EscortHoldRegistry.clear();

        assertEquals(Set.of(), EscortHoldRegistry.heldBy(player));
        assertFalse(EscortHoldRegistry.isReleasePending(escortee));
    }
}
