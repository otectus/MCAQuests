package dev.otectus.mcaquests.compat.bountiful;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-tick window that keeps one cash-in from being credited twice.
 *
 * <p>Worth its own test because the failure it prevents is invisible from inside the game: a
 * "complete two bounties" quest finished by one bounty looks exactly like a quest that worked. The
 * cache is the only thing standing between a hook that observes another mod's method — reachable from
 * a board, from a command, and once per hand — and that outcome.
 *
 * <p>The clock is injected precisely so this can be asserted tick by tick, with no server and no
 * waiting.
 */
class CompletionDedupeTest {

    private final AtomicLong tick = new AtomicLong();
    private final CompletionDedupe dedupe = new CompletionDedupe(tick::get);

    @Test
    @DisplayName("the same bounty reported twice inside the window is credited once")
    void duplicateWithinTheWindowIsRejected() {
        assertTrue(dedupe.accept("bounty"), "the first report is the real one");
        assertFalse(dedupe.accept("bounty"), "the off-hand repeat on the same tick must not count");

        tick.set(1);
        assertFalse(dedupe.accept("bounty"), "still inside the two-tick window");
    }

    @Test
    @DisplayName("the same key is accepted again once the window has passed")
    void theWindowExpires() {
        assertTrue(dedupe.accept("bounty"));

        tick.set(3);
        dedupe.sweep(tick.get());
        assertTrue(dedupe.accept("bounty"),
                "a second, genuinely different cash-in of the same shape of bounty is a real thing to "
                        + "do at a board, and must be credited");
    }

    @Test
    @DisplayName("two different bounties on the same tick both count")
    void distinctKeysOnOneTickBothCount() {
        assertTrue(dedupe.accept("first"));
        assertTrue(dedupe.accept("second"),
                "the window collapses repeats of one action, not two different actions");
    }

    @Test
    @DisplayName("the map does not grow with the number of cash-ins")
    void staysBounded() {
        for (int i = 0; i < 4096; i++) {
            tick.set(i);
            dedupe.accept("bounty-" + i);
        }
        assertTrue(dedupe.size() <= 512,
                "a mod firing the hook in a loop must not be able to grow the cache without bound");

        tick.set(tick.get() + CompletionDedupe.TTL_TICKS);
        dedupe.sweep(tick.get());
        assertEquals(0, dedupe.size(), "everything expires; nothing is held past its window");
    }
}
