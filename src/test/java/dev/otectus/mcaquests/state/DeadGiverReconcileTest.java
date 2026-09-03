package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The offline half of "the giver died" (F-B03): the decision and the record it reads.
 *
 * <p>{@code onGiverDeath} only ever reconciled players who were online, so anyone else kept an active
 * quest pointing at a villager who no longer exists and {@code fail_on_giver_death} never fired for
 * them. Login now applies the same rule — {@link TurnInMode#failsOnGiverDeath}, which is why that rule
 * is a pure static — against {@link DeadGiversData}, the deaths that were written down.
 *
 * <p>The deliberate non-case is the last assertion: a giver who is merely unloaded is not recorded, so
 * nothing at all happens to their quests. Failing on "not in memory" would take quests away for a chunk
 * boundary.
 */
class DeadGiverReconcileTest {

    private static final UUID GIVER = UUID.randomUUID();
    private static final UUID OTHER_GIVER = UUID.randomUUID();

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static Optional<FailureSpec> failOnGiverDeath() {
        return Optional.of(new FailureSpec(Optional.empty(), Optional.empty(), Optional.empty(), true, 0,
                Optional.empty(), false));
    }

    private static Optional<FailureSpec> deadlineOnly() {
        return Optional.of(new FailureSpec(Optional.of(6000), Optional.empty(), Optional.empty(), false, 0,
                Optional.empty(), false));
    }

    @Test
    @DisplayName("fail_on_giver_death fails the quest; another failure block leaves it alone")
    void perQuestOptIn() {
        assertTrue(TurnInMode.ORIGINAL_GIVER.failsOnGiverDeath(failOnGiverDeath(), false));
        assertTrue(TurnInMode.SELF_COMPLETE.failsOnGiverDeath(failOnGiverDeath(), false),
                "the per-quest opt-in does not care how the quest is handed in");
        assertFalse(TurnInMode.ORIGINAL_GIVER.failsOnGiverDeath(deadlineOnly(), false),
                "a deadline says nothing about the giver dying");
        assertFalse(TurnInMode.ORIGINAL_GIVER.failsOnGiverDeath(Optional.empty(), false),
                "with the config off, a quest with no failure block waits for an abandon");
    }

    @Test
    @DisplayName("the config only applies to a quest handed back to its original giver")
    void globalConfigIsScopedToOriginalGiver() {
        assertTrue(TurnInMode.ORIGINAL_GIVER.failsOnGiverDeath(Optional.empty(), true));
        assertFalse(TurnInMode.SELF_COMPLETE.failsOnGiverDeath(Optional.empty(), true),
                "a self-completing quest needs nobody to hand it to");
    }

    @Test
    @DisplayName("only a recorded death counts, and it survives a save/load round trip")
    void deathsArePersistedAndScoped() {
        DeadGiversData data = new DeadGiversData();
        assertTrue(data.isEmpty(), "a fresh world has nothing on the books");

        data.record(GIVER, 1000L);

        assertTrue(data.isDead(GIVER));
        assertFalse(data.isDead(OTHER_GIVER), "an unloaded villager is not a dead one");

        DeadGiversData reloaded = DeadGiversData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.isDead(GIVER), "a player who returns after a restart must still be reconciled");
        assertFalse(reloaded.isDead(OTHER_GIVER));
    }

    @Test
    @DisplayName("a death older than the retention window is pruned rather than kept forever")
    void oldDeathsArePruned() {
        DeadGiversData data = new DeadGiversData();
        data.record(GIVER, 0L);
        data.record(OTHER_GIVER, DeadGiversData.RETENTION_TICKS + 1L);

        assertFalse(data.isDead(GIVER), "the old entry is dropped when a newer death is recorded");
        assertTrue(data.isDead(OTHER_GIVER));
    }
}
