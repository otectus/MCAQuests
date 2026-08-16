package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A single shared requirement of a project phase (spec 0.4.0) — the community analogue of
 * {@code QuestObjective}. Progress lives in a shared {@link SharedObjectiveProgress} (server-owned),
 * not on any one player, so multiple players contribute to the same counter.
 *
 * <p>Two flavours:
 * <ul>
 *   <li><b>contribution</b> objectives ({@link #isContribution()} true) are advanced when a player
 *       presents items/work to a sponsor: {@link #contribute} validates and consumes server-side, then
 *       banks into the shared pool. {@code donate_item} is the canonical example.</li>
 *   <li><b>event-driven</b> objectives ({@link #isEventDriven()} true) are credited continuously by
 *       {@code ProjectProgressEvents} (kills/placement/talk) when the acting player is a permitted
 *       contributor in the project's scope.</li>
 * </ul>
 */
public interface ProjectObjective {

    ProjectObjectiveType<?> type();

    /** Human-readable one-line summary for the project card, e.g. "Donate 128 Cobblestone". */
    Component describe();

    /** Target amount. */
    int required();

    /** Shared amount toward {@link #required()} (clamped). */
    default int current(SharedObjectiveProgress progress) {
        return Math.min(progress.count(), required());
    }

    default boolean isSatisfied(SharedObjectiveProgress progress) {
        return progress.count() >= required();
    }

    /** True if progress accumulates via game events rather than a sponsor contribution. */
    boolean isEventDriven();

    /** True if this objective is advanced by presenting items/work to a sponsor (a donate-style click). */
    default boolean isContribution() {
        return false;
    }

    /** Per-player cap on this objective (0 = use the config default). */
    default int perPlayerCap() {
        return 0;
    }

    /**
     * Validate, consume from {@code player}, and bank into {@code progress} atomically (server thread).
     * {@code effectiveCap} is the resolved per-player cap (0 = unlimited). Returns the amount banked
     * (0 if nothing could be contributed). Only meaningful when {@link #isContribution()} is true.
     */
    default int contribute(ServerPlayer player, SharedObjectiveProgress progress, int effectiveCap) {
        return 0;
    }
}
