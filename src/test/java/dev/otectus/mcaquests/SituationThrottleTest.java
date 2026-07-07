package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.situation.SituationThrottle;
import dev.otectus.mcaquests.quest.situation.SituationThrottle.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for the situation open throttle (0.8.0). */
class SituationThrottleTest {

    @Test
    void allowsWhenUnderCapAndOffCooldown() {
        Decision d = SituationThrottle.evaluate(1, 2, Long.MIN_VALUE, Long.MIN_VALUE, 1000L);
        assertEquals(Decision.ALLOWED, d);
        assertTrue(d.allowed());
    }

    @Test
    void cappedWhenAtConcurrencyLimit() {
        assertEquals(Decision.CAPPED, SituationThrottle.evaluate(2, 2, Long.MIN_VALUE, Long.MIN_VALUE, 1000L));
        assertEquals(Decision.CAPPED, SituationThrottle.evaluate(5, 2, Long.MIN_VALUE, Long.MIN_VALUE, 1000L));
    }

    @Test
    void zeroCapDisablesConcurrencyLimit() {
        assertEquals(Decision.ALLOWED, SituationThrottle.evaluate(100, 0, Long.MIN_VALUE, Long.MIN_VALUE, 1000L));
    }

    @Test
    void perDefinitionCooldownBlocksUntilElapsed() {
        assertEquals(Decision.ON_COOLDOWN, SituationThrottle.evaluate(0, 2, 5000L, Long.MIN_VALUE, 4999L));
        assertEquals(Decision.ALLOWED, SituationThrottle.evaluate(0, 2, 5000L, Long.MIN_VALUE, 5000L));
    }

    @Test
    void globalCooldownBlocksAfterPerDefinitionPasses() {
        assertEquals(Decision.GLOBAL_COOLDOWN, SituationThrottle.evaluate(0, 2, Long.MIN_VALUE, 6000L, 5999L));
        assertEquals(Decision.ALLOWED, SituationThrottle.evaluate(0, 2, Long.MIN_VALUE, 6000L, 6000L));
    }

    @Test
    void capTakesPrecedenceOverCooldownReasons() {
        // When both capped and on cooldown, the cap is reported first.
        assertEquals(Decision.CAPPED, SituationThrottle.evaluate(2, 2, 9000L, 9000L, 1000L));
        assertFalse(SituationThrottle.evaluate(2, 2, 9000L, 9000L, 1000L).allowed());
    }
}
