package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * A project objective that watches the world instead of waiting to be told about it — the third
 * flavour alongside contribution and event-driven objectives (Townstead spec §5.4).
 *
 * <p>The existing two both need someone to <em>do</em> something the game can announce: hand items to
 * a sponsor, kill a mob, place a block. "The village has three docks", "the workforce has reached tier
 * two", "everybody has been well fed for a while" are not events at all. They are conditions that
 * become true quietly, often while no player is nearby, and the only honest way to notice is to look.
 *
 * <p>Polling is deliberately not the same as abusing an event. It runs on a bounded, throttled pass
 * ({@code compat.townstead.projectPollIntervalTicks}) and each implementation is expected to be cheap
 * and idempotent, returning {@code true} only when this call actually changed something — the same
 * contract {@code PollingObjective} carries on the quest side.
 *
 * <p><b>State-driven completion earns no contribution credit.</b> Nobody handed anything over, so
 * nobody is recorded as having; the {@code CONTRIBUTORS} and {@code TOP_CONTRIBUTOR} reward targets
 * genuinely have nobody to pay, and that is surfaced rather than papered over with invented numbers.
 */
public interface PollingProjectObjective extends ProjectObjective {

    /**
     * Re-reads the world and updates shared progress.
     *
     * @param level the project's anchor dimension, already resolved
     * @return true only when this call changed progress, so the pass knows whether to save and re-sync
     */
    boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                 ProjectState state, SharedObjectiveProgress progress);

    /** Polled objectives are not credited by events. */
    @Override
    default boolean isEventDriven() {
        return false;
    }
}
