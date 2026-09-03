package dev.otectus.mcaquests.quest.guidance;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players whose guidance is known to be out of date, coalesced.
 *
 * <p>Guidance used to be recomputed only by the once-a-second pass, so accepting a quest could leave
 * the player without a destination for most of a second — long enough to look broken on a button
 * press. Marking instead of recomputing is what makes that immediate without making it expensive: a
 * turn-in that finishes a chain and starts the next one marks the same player three times and is
 * recomputed once, at the end of the tick.
 *
 * <p>A set of ids rather than of players, because the mutation and the recompute are separated by the
 * rest of the tick and the player may have logged out in between — the drain resolves ids against the
 * live player list and simply skips the ones that are gone. Concurrent because objective handlers can
 * mark from any thread the server dispatches them on.
 */
public final class GuidanceDirtySet {

    private final Set<UUID> marked = ConcurrentHashMap.newKeySet();

    /** Notes that {@code playerId}'s destinations may have changed. Repeats cost nothing. */
    public void mark(UUID playerId) {
        if (playerId != null) {
            marked.add(playerId);
        }
    }

    /**
     * Takes every marked player and empties the set in one step.
     *
     * <p>Removal by iteration rather than a copy-then-clear, so a mark that arrives during the drain is
     * either handled by this pass or kept for the next one, and never dropped between the two.
     */
    public Set<UUID> drain() {
        if (marked.isEmpty()) {
            return Set.of();
        }
        Set<UUID> drained = ConcurrentHashMap.newKeySet();
        for (UUID id : marked) {
            if (marked.remove(id)) {
                drained.add(id);
            }
        }
        return drained;
    }

    /** Whether anything is waiting, so the common tick can return without allocating. */
    public boolean isEmpty() {
        return marked.isEmpty();
    }
}
