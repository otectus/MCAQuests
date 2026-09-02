package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadQuery;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Set;

/** Shared plumbing for the Townstead objective types. */
final class TownsteadObjectives {

    private TownsteadObjectives() {
    }

    /**
     * Exactly the capabilities a query needs — <b>derived from its path, not just its source</b>.
     *
     * <p>Precision matters here. If every villager query claimed {@code READ_VILLAGER} alone, a
     * Townstead release that moved its needs accessors would leave hunger quests polling a snapshot
     * whose needs are all zero and completing on garbage. If instead every villager query claimed all
     * of needs, schedule and profession, one missing accessor would suspend quests that never touched
     * it. Reading the first path segment costs nothing and gets both cases right.
     */
    static Set<TownsteadCapability> capabilitiesFor(TownsteadQuery query) {
        EnumSet<TownsteadCapability> needed = EnumSet.noneOf(TownsteadCapability.class);
        switch (query.source()) {
            case CALENDAR -> needed.add(TownsteadCapability.READ_CALENDAR);
            case BUILDING -> needed.add(TownsteadCapability.READ_BUILDING);
            case SPIRIT -> needed.add(TownsteadCapability.READ_SPIRIT);
            case GENE -> needed.add(TownsteadCapability.READ_GENE);
            case ROOT -> {
                needed.add(TownsteadCapability.READ_VILLAGER); // the root id comes off the villager
                needed.add(TownsteadCapability.READ_ROOT);
            }
            case VILLAGER -> {
                needed.add(TownsteadCapability.READ_VILLAGER);
                String head = query.path().isEmpty() ? "" : query.path().get(0);
                if (head.equals("needs")) {
                    needed.add(TownsteadCapability.READ_NEEDS);
                } else if (head.equals("schedule")) {
                    needed.add(TownsteadCapability.READ_SCHEDULE);
                } else if (head.startsWith("profession")) {
                    needed.add(TownsteadCapability.READ_PROFESSION);
                }
            }
        }
        return Collections.unmodifiableSet(needed);
    }

    /** Scratch key holding the game time a hold objective was last polled. */
    private static final String K_POLL_AT = "poll_at";

    /**
     * How much time a hold objective may credit for this poll, and stamps the clock for the next one.
     *
     * <p>The hold objectives used to credit a flat twenty ticks per poll, which is only the truth while
     * {@code townsteadPollIntervalTicks} is at its default of twenty. At the top of its range a
     * thirty-second hold wanted thirty real minutes, and {@code reset_on_false} made it near-unfinishable.
     * Measuring the gap is what {@code schedule_streak} already did.
     *
     * <p>The first poll credits one interval rather than nothing, so a hold starts moving immediately, and
     * the gap is capped at four intervals so a server that skipped ticks — or a world rolled back — cannot
     * hand over a hold for free.
     */
    static long elapsedSincePoll(ObjectiveProgress progress, long now, int intervalTicks) {
        long interval = Math.max(1L, intervalTicks);
        CompoundTag extra = progress.extra();
        boolean first = !extra.contains(K_POLL_AT);
        long since = first ? interval : Math.min(Math.max(0L, now - extra.getLong(K_POLL_AT)), interval * 4L);
        extra.putLong(K_POLL_AT, now);
        return since;
    }

    /**
     * The villager a query is about, once its quest is active. Returns {@code null} for a global source
     * (nothing to resolve) and for a target that cannot be found, which the caller turns into the
     * query's {@code missing} answer rather than into a comparison against a default.
     */
    @Nullable
    static Entity subjectEntity(TownsteadQuery query, ServerPlayer player, ActiveQuest quest,
                                ObjectiveProgress progress, ServerLevel level) {
        if (query.source().isGlobal()) {
            return null;
        }
        return TownsteadTargetResolver
                .resolveForObjective(query.target(), player, quest, progress, level)
                .orElse(null);
    }
}
