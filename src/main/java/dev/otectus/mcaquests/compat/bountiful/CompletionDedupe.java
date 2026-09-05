package dev.otectus.mcaquests.compat.bountiful;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Lets one cash-in be credited once, however many times it is reported.
 *
 * <p>The hook observes another mod's method, and there is more than one way for a single completion
 * to arrive twice: Bountiful's own board and its command both call {@code tryCashIn}, a board can be
 * clicked with both hands on the same tick, and a mixin that ends up applied to two builds of the
 * same class would double every event. Crediting a "complete two bounties" quest from one bounty is
 * silent and unfixable from the player's side, so the guard is here rather than in the objective.
 *
 * <p>Two ticks of memory, not more. The window only has to span the ways one player action can be
 * reported repeatedly, and a longer one would start swallowing a second, genuinely different bounty
 * cashed in immediately after the first — a real thing to do at a board with several notices on it.
 *
 * <p>The clock is injected so the whole thing is testable without a server: the class holds no
 * Minecraft type at all, and the caller supplies the game time it is reasoning about.
 */
public final class CompletionDedupe {

    /** How many ticks a key is remembered for. See the class javadoc for why it is this short. */
    public static final long TTL_TICKS = 2;

    /**
     * A ceiling on the map, so a mod firing the hook in a loop cannot grow it without bound. Far above
     * any real number of cash-ins in a two-tick window; this is a floor under pathological input, not
     * an expected size.
     */
    private static final int CAP = 512;

    private final Map<String, Long> seen = new HashMap<>();
    private final LongSupplier clock;

    /**
     * @param clock the current server game time in ticks, read on every call rather than passed in, so
     *              a caller cannot accept two reports under one stale timestamp
     */
    public CompletionDedupe(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * True the first time {@code key} is seen, false while it is still remembered.
     *
     * <p>Sweeping on the way in as well as on the tick event is what makes the class correct on its
     * own: a sweep that only ever ran from a tick handler would leave the very first duplicate of a
     * server that is between ticks — or in a unit test — unguarded.
     */
    public synchronized boolean accept(String key) {
        long now = clock.getAsLong();
        sweep(now);
        return seen.putIfAbsent(key, now) == null;
    }

    /**
     * Drops everything older than {@link #TTL_TICKS}. Called from the server tick as well as from
     * {@link #accept}, so a burst that is never followed by another cash-in still frees its keys.
     */
    public synchronized void sweep(long now) {
        seen.values().removeIf(when -> now - when >= TTL_TICKS);
        if (seen.size() > CAP) {
            // Everything here expires within two ticks anyway, so dropping the lot costs at most one
            // duplicated credit and cannot leak.
            seen.clear();
        }
    }

    /** How many keys are currently remembered. Diagnostics and tests only. */
    public synchronized int size() {
        return seen.size();
    }
}
