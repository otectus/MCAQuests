package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.map.MapSyncDirtyFlag;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.resources.ResourceLocation;

/**
 * Client-side copy of where the server is currently sending this player.
 *
 * <p>Read every frame by {@code QuestMarkerRenderer}, once a frame by {@code QuestHudOverlay}, on
 * every rebuild by {@code QuestLogScreen}, and on every client tick by {@code QuestWaypointSync} —
 * and written from the network thread's work queue, hence {@code volatile} plus
 * replace-don't-mutate, the same shape as {@link ClientHighlightData} and {@link ClientQuestData}.
 *
 * <p>One entry per quest, and exactly one of them marked primary. The server decides which quest and
 * which objective each answer is about, so there is no list here to filter and no chance of the beam
 * and the villager outline disagreeing — {@link #primary()} is the only thing the marker ever reads.
 */
public final class ClientGuidanceData {

    private static volatile GuidanceSnapshot snapshot = GuidanceSnapshot.EMPTY;
    /**
     * Counts replacements of {@link #snapshot}, so a reader can tell one apart from the next.
     *
     * <p>Atomic rather than a {@code volatile long}: the writer is the network thread's work queue and
     * the readers are the render and client threads, and {@code revision++} on a volatile field is a
     * read and a write with a gap in the middle — two snapshots landing in one tick could leave the
     * number where it started and a cached anchor believing it was still current.
     */
    private static final AtomicLong REVISION = new AtomicLong();

    private ClientGuidanceData() {
    }

    public static void update(GuidanceSnapshot updated) {
        snapshot = updated == null ? GuidanceSnapshot.EMPTY : updated;
        REVISION.incrementAndGet();
        MapSyncDirtyFlag.set();
    }

    /**
     * How many snapshots have arrived, as a cheap "has this changed" signal.
     *
     * <p>The marker resolves a fixed target's support surface by reading the world, which is far too
     * much work to do every frame and wrong to cache forever. Comparing this number is how it knows
     * the answer it cached is still about the same target.
     *
     * <p>The map layer is told rather than left to compare: every write here sets
     * {@link MapSyncDirtyFlag}, which is what replaced twenty diffs a second with one per packet.
     */
    public static long revision() {
        return REVISION.get();
    }

    /** Every quest that can say where it is sending the player, in quest-log order. */
    public static List<ActiveGuidance> all() {
        return snapshot.all();
    }

    /** The one the world marker stands on and the villager outline belongs to, or empty. */
    public static Optional<ActiveGuidance> primary() {
        return snapshot.primaryGuidance();
    }

    /** Where this particular quest is sending the player, or empty when it cannot say. */
    public static Optional<ActiveGuidance> forQuest(ResourceLocation questId, UUID villagerUuid) {
        for (ActiveGuidance guidance : snapshot.all()) {
            if (guidance.isAbout(questId, villagerUuid)) {
                return Optional.of(guidance);
            }
        }
        return Optional.empty();
    }

    /** Drops it all — on disconnect, so a marker cannot survive into the next world. */
    public static void clear() {
        snapshot = GuidanceSnapshot.EMPTY;
        REVISION.incrementAndGet();
        MapSyncDirtyFlag.set();
    }
}
