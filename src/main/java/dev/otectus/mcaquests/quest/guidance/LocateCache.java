package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Remembers where a world search found something, so it is never run twice for the same objective.
 *
 * <p>Biome searches run synchronously; structure searches use {@link StructureSearches} and are
 * polled through {@link #resolveAsync}. Guidance is recomputed roughly once a second per player,
 * so remembering verified answers avoids repeating world searches for an unchanged objective.
 *
 * <p>A successful search's answer is written into that objective's
 * own {@link ObjectiveProgress#extra()} — which is already persisted, already per-quest-instance, and
 * already how {@code EscortEntityObjective} freezes its destination. A restart or a relog therefore
 * costs nothing: the fortress is still where it was.
 *
 * <p>A search that finds <em>nothing</em> is also remembered, but only for
 * {@code guidanceSearchIntervalTicks}, because "nothing within range" is a fact about where the
 * player was standing rather than about the world. Walking a thousand blocks and asking again is
 * reasonable; asking again next second is not.
 *
 * <p>The dimension is stored beside the position. An answer found in the overworld means nothing in
 * the Nether, and reusing it there would plant a marker at a coordinate that merely happens to
 * divide by eight.
 *
 * <h2>The per-pass budget</h2>
 *
 * <p>Guidance asks every active quest for a destination. Synchronous biome and block searches
 * therefore share a per-pass budget. Structure polling is exempt: its actual work is limited by
 * the server-wide queue, and a pending search must not starve the player's other objectives.
 *
 * <p>{@link #beginPass(int)} therefore opens a pass with a budget of {@code guidanceSearchesPerPass}
 * synchronous searches; once it is spent, further misses answer empty and <b>record nothing</b>, so the
 * quests that did not get a turn are tried on the next pass rather than being throttled for a reason
 * that has nothing to do with the world. A cached hit is a tag read and never touches the budget.
 *
 * <p>The budget is a plain static field rather than a parameter because {@link #resolve} is reached
 * from inside {@code QuestObjective.guidance}, which has no budget to pass and should not grow one.
 * It is only ever written between {@link #beginPass} and {@link #endPass} on the server thread, and
 * outside a pass it is {@link #UNLIMITED} — which is what an objective's own {@code poll} sees, and
 * is right, because a poll runs once for the objective that needs it rather than once per quest.
 */
public final class LocateCache {

    /** The shipped default, used when no config is attached (a unit test). */
    private static final int DEFAULT_RETRY_TICKS = 200;
    /** The shipped default budget, used when no config is attached (a unit test). */
    private static final int DEFAULT_SEARCHES_PER_PASS = 1;
    /** No pass is open: every caller may search. */
    private static final int UNLIMITED = -1;

    /** Searches left in the open guidance pass, or {@link #UNLIMITED} when none is open. */
    private static int remainingSearches = UNLIMITED;

    private LocateCache() {
    }

    /** Opens a guidance pass that may run at most {@code budget} real world searches. */
    public static void beginPass(int budget) {
        remainingSearches = Math.max(0, budget);
    }

    /** Opens a guidance pass with the configured budget. */
    public static void beginPass() {
        beginPass(searchesPerPass());
    }

    /** Closes the pass, restoring the unlimited default. Safe to call when none is open. */
    public static void endPass() {
        remainingSearches = UNLIMITED;
    }

    /**
     * Takes one search from the open pass's budget, or reports that there is none left.
     *
     * <p>Always true outside a pass, so nothing that was allowed to search before this existed has
     * stopped being allowed to.
     */
    private static boolean spendSearch() {
        if (remainingSearches == UNLIMITED) {
            return true;
        }
        if (remainingSearches == 0) {
            return false;
        }
        remainingSearches--;
        return true;
    }

    /**
     * The remembered position for {@code key}, or the result of running {@code search} and
     * remembering it. Empty when the search has already run recently and found nothing, or when this
     * pass's search budget is spent.
     *
     * @param key a short prefix unique within one objective's {@code extra} tag, so two searches on
     *            the same objective (a structure and a biome, say) cannot overwrite each other
     */
    public static Optional<BlockPos> resolve(ObjectiveProgress progress, String key, ServerLevel level,
                                             Supplier<Optional<BlockPos>> search) {
        CompoundTag extra = progress.extra();
        String dimension = level.dimension().location().toString();
        boolean sameDimension = dimension.equals(extra.getString(key + "Dim"));
        if (sameDimension && extra.contains(key + "X")) {
            return Optional.of(new BlockPos(extra.getInt(key + "X"), extra.getInt(key + "Y"),
                    extra.getInt(key + "Z")));
        }
        long now = level.getGameTime();
        if (sameDimension && extra.contains(key + "Tried")
                && now - extra.getLong(key + "Tried") < retryInterval()) {
            return Optional.empty();
        }
        // Before anything is written: a search this pass had no budget for is not a search that found
        // nothing, and recording it as one would silence this objective for the whole retry interval.
        if (!spendSearch()) {
            return Optional.empty();
        }
        if (!sameDimension) {
            forget(progress, key);
        }
        extra.putLong(key + "Tried", now);
        extra.putString(key + "Dim", dimension);
        Optional<BlockPos> found = search.get();
        found.ifPresent(pos -> {
            extra.putInt(key + "X", pos.getX());
            extra.putInt(key + "Y", pos.getY());
            extra.putInt(key + "Z", pos.getZ());
        });
        return found;
    }

    /**
     * Polls queued work without treating "pending" as a failed search. The global structure queue
     * budgets actual work; polling its shared futures must not consume the synchronous per-pass
     * budget or one pending quest would prevent every later quest from starting. No callback holds
     * player/progress state: only the current level's result can be written, on this thread.
     */
    public static Optional<BlockPos> resolveAsync(ObjectiveProgress progress, String key, ServerLevel level,
            Supplier<CompletableFuture<Optional<BlockPos>>> search) {
        return resolveAsync(progress, key, level.dimension().location().toString(), level.getGameTime(),
                retryInterval(), search);
    }

    static Optional<BlockPos> resolveAsync(ObjectiveProgress progress, String key, String dimension,
            long now, int retryTicks, Supplier<CompletableFuture<Optional<BlockPos>>> search) {
        CompoundTag extra = progress.extra();
        boolean sameDimension = dimension.equals(extra.getString(key + "Dim"));
        if (sameDimension && extra.contains(key + "X")) {
            return Optional.of(new BlockPos(extra.getInt(key + "X"), extra.getInt(key + "Y"),
                    extra.getInt(key + "Z")));
        }
        if (sameDimension && extra.contains(key + "Tried")
                && now - extra.getLong(key + "Tried") < retryTicks) return Optional.empty();
        if (!sameDimension) forget(progress, key);
        CompletableFuture<Optional<BlockPos>> future = search.get();
        if (!future.isDone()) return Optional.empty();
        Optional<BlockPos> found = future.isCompletedExceptionally() ? Optional.empty()
                : future.getNow(Optional.empty());
        extra.putString(key + "Dim", dimension);
        extra.putLong(key + "Tried", now);
        found.ifPresent(pos -> {
            extra.putInt(key + "X", pos.getX());
            extra.putInt(key + "Y", pos.getY());
            extra.putInt(key + "Z", pos.getZ());
        });
        return found;
    }

    /** Drops a remembered answer, so the next {@link #resolve} searches again. */
    public static void forget(ObjectiveProgress progress, String key) {
        CompoundTag extra = progress.extra();
        extra.remove(key + "X");
        extra.remove(key + "Y");
        extra.remove(key + "Z");
        extra.remove(key + "Tried");
        extra.remove(key + "Dim");
    }

    private static int retryInterval() {
        try {
            return McaQuestsConfig.COMMON.guidanceSearchIntervalTicks.get();
        } catch (RuntimeException e) {
            return DEFAULT_RETRY_TICKS;
        }
    }

    private static int searchesPerPass() {
        try {
            return McaQuestsConfig.COMMON.guidanceSearchesPerPass.get();
        } catch (RuntimeException e) {
            return DEFAULT_SEARCHES_PER_PASS;
        }
    }
}
