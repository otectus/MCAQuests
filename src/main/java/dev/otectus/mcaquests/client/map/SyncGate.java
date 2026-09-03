package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.ClearCause;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;

/**
 * Whether this client tick has anything for the map layer to do, and what changed if it has.
 *
 * <p>All of {@code QuestWaypointSync}'s decisions and none of its calls. The sync needs
 * {@code Minecraft} to answer "which level, which dimension, is the player here"; the rules that turn
 * those answers into "clear everything and start again" or "do nothing at all" need no game, and are
 * exactly the rules the lifecycle matrix in the spec is about. So they live here, where a test can
 * drive a login, a dimension change and a rejoin in three lines.
 *
 * <h2>Why the level is compared by identity</h2>
 *
 * <p>Two worlds can have the same {@link ResourceKey}: leaving a server and joining another one puts
 * the player back in {@code minecraft:overworld}, and a dimension comparison alone would call that no
 * change and leave the previous world's waypoints on the map. The {@code ClientLevel} object is
 * different, though, so the reference is the honest question — held weakly, because this is a static
 * lifetime and a level that has been unloaded must be collectable.
 */
public final class SyncGate {

    /** The last level object, by reference. Weak so a departed world is not kept alive by a gate. */
    private WeakReference<Object> lastLevel = new WeakReference<>(null);
    @Nullable
    private ResourceKey<Level> lastDimension;
    private long worldEpoch;
    /** When the reconciler asked to be called again after a failure, or 0 when it did not. */
    private long nextRetryAtMillis;

    /**
     * What the tick should do.
     *
     * @param clear      the reason to take every automatic waypoint off the maps first, or
     *                   {@code null} when nothing has ended
     * @param reconcile  whether the desired set is worth building and applying at all. False is the
     *                   common answer, and the one that must allocate nothing upstream
     * @param worldEpoch the epoch the pass belongs to, for the diagnostic report
     */
    public record Decision(@Nullable ClearCause clear, boolean reconcile, long worldEpoch) {
    }

    /**
     * Reads the tick.
     *
     * @param level     the current client level, compared by reference only — never dereferenced here
     * @param dimension that level's dimension key
     * @param dirty     whether something already said the desired set may have changed. Consumed by
     *                  the caller, so a tick that also changes world does not drop it
     * @param nowMillis wall clock, for the retry that a failing backend asked for
     */
    public Decision evaluate(Object level, ResourceKey<Level> dimension, boolean dirty, long nowMillis) {
        Object previousLevel = lastLevel.get();
        if (previousLevel != level) {
            // A different world object: either the first one this session, or another one entirely.
            ClearCause cause = previousLevel == null ? null : ClearCause.LEVEL_CHANGE;
            lastLevel = new WeakReference<>(level);
            lastDimension = dimension;
            worldEpoch++;
            nextRetryAtMillis = 0L;
            return new Decision(cause, true, worldEpoch);
        }
        if (!dimension.equals(lastDimension)) {
            // Same world, new dimension. Xaero's waypoints have no dimension of their own and a
            // coordinate from the overworld means something else in the Nether, so the set is rebuilt
            // rather than adjusted.
            lastDimension = dimension;
            worldEpoch++;
            nextRetryAtMillis = 0L;
            return new Decision(ClearCause.DIMENSION_CHANGE, true, worldEpoch);
        }
        boolean retryDue = nextRetryAtMillis != 0L && nowMillis >= nextRetryAtMillis;
        return new Decision(null, dirty || retryDue, worldEpoch);
    }

    /** Records when the slowest backend in backoff wants to be called again; 0 for none. */
    public void retryAt(long millis) {
        nextRetryAtMillis = millis;
    }

    /** Counts world and dimension changes; a report from an older epoch is a stale one. */
    public long worldEpoch() {
        return worldEpoch;
    }

    /** Bumps the epoch for something that ended the world without changing the level object. */
    public long nextEpoch() {
        nextRetryAtMillis = 0L;
        return ++worldEpoch;
    }

    /** Forgets the world entirely, on logout: the next level seen is a first one, not a change. */
    public void reset() {
        lastLevel = new WeakReference<>(null);
        lastDimension = null;
        nextRetryAtMillis = 0L;
        worldEpoch++;
    }
}
