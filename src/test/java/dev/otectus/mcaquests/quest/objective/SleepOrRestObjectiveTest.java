package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which event finishes a {@link SleepOrRestObjective}.
 *
 * <p>{@code require_morning} used to decide nothing at all: morning arriving credited every sleep
 * objective, and nothing else credited any of them. The two predicates here are the split — one event
 * each, and no overlap — so a pack that asked for a rest rather than a dawn gets one.
 */
class SleepOrRestObjectiveTest {

    static {
        // The objective's codec initialises with the class.
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("require_morning defaults to true")
    void defaultRequiresMorning() {
        assertTrue(new SleepOrRestObjective(true).requireMorning());
    }

    @Test
    @DisplayName("morning arriving credits only the objectives that asked for morning")
    void morningCreditsOnlyRequireMorning() {
        assertTrue(SleepOrRestObjective.creditsOnMorning(true));
        assertFalse(SleepOrRestObjective.creditsOnMorning(false),
                "an objective asking for a rest is not finished by somebody else's sunrise");
    }

    @Test
    @DisplayName("getting up credits only the objectives that did not ask for morning")
    void wakeCreditsOnlyRest() {
        assertTrue(SleepOrRestObjective.creditsOnWake(false, false, true));
        assertFalse(SleepOrRestObjective.creditsOnWake(true, false, true),
                "an objective asking for morning waits for morning, not for the player to stand up");
    }

    @Test
    @DisplayName("a bounce-out of bed is never a rest")
    void wakeImmediatelyNeverCredits() {
        assertFalse(SleepOrRestObjective.creditsOnWake(false, true, true));
        assertFalse(SleepOrRestObjective.creditsOnWake(true, true, true));
    }

    @Test
    @DisplayName("lying down briefly is not a rest either")
    void shortSleepDoesNotCredit() {
        assertFalse(SleepOrRestObjective.creditsOnWake(false, false, false));
    }
}
