package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tending boundary of {@link HealEntityObjective}.
 *
 * <p>The interesting case is the default threshold of {@code 1.0}, which read as a plain comparison
 * lets a player credit "tend to the hurt villager" on a villager who is perfectly well.
 */
class HealEntityObjectiveTest {

    private static final double DEFAULT_THRESHOLD = 1.0D;

    static {
        // The objective's codec fields reach BuiltInRegistries the moment the class initialises.
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a villager at full health is never tended, even at the default threshold")
    void fullHealthNeverCredits() {
        assertFalse(HealEntityObjective.qualifiesForTending(1.0D, DEFAULT_THRESHOLD));
        assertFalse(HealEntityObjective.qualifiesForTending(1.0D, 0.5D));
    }

    @Test
    @DisplayName("the default threshold means 'hurt at all'")
    void defaultThresholdAcceptsAnyWound() {
        assertTrue(HealEntityObjective.qualifiesForTending(0.99D, DEFAULT_THRESHOLD));
        assertTrue(HealEntityObjective.qualifiesForTending(0.01D, DEFAULT_THRESHOLD));
    }

    @Test
    @DisplayName("a threshold below full keeps its plain 'at or below this much health' meaning")
    void explicitThresholdIsInclusive() {
        assertTrue(HealEntityObjective.qualifiesForTending(0.4D, 0.5D));
        assertTrue(HealEntityObjective.qualifiesForTending(0.5D, 0.5D), "the boundary itself counts");
        assertFalse(HealEntityObjective.qualifiesForTending(0.6D, 0.5D));
    }

    @Test
    @DisplayName("an unreadable health fraction does not credit")
    void unknownFractionIsRejected() {
        /* McaCompat answers 1.0 when it cannot read a villager's health, and full health never counts. */
        double unknown = 1.0D;
        assertFalse(HealEntityObjective.qualifiesForTending(unknown, DEFAULT_THRESHOLD));
    }
}
