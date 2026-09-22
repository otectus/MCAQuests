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

    /**
     * Called once when the phase holding this objective becomes current — for a new instance, a phase
     * advance, a seeded follow-up and an operator repair alike ({@code ProjectPhases}). The place to
     * capture any reading that progress will be measured from, so it is taken at the phase boundary and
     * not on whichever periodic poll happens to come first (1.6.6).
     */
    default void onPhaseEntered(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                                ProjectState state, int objectiveIndex, SharedObjectiveProgress progress) {
    }

    /**
     * True while this objective is waiting for a reading it could not take at the phase boundary. The
     * phase counts as unavailable meanwhile, so its clock is paused rather than running on an objective
     * that cannot yet measure anything.
     */
    default boolean isPending(ProjectState state, SharedObjectiveProgress progress) {
        return false;
    }

    /**
     * Retries a pending reading. Returns true when it changed persistent state. Runs before availability
     * is judged on every sweep, so a pending objective resolves as soon as its source can be read.
     */
    default boolean resolvePending(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                                   ProjectState state, SharedObjectiveProgress progress) {
        return false;
    }

    /** Polled objectives are not credited by events. */
    @Override
    default boolean isEventDriven() {
        return false;
    }
}
