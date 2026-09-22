package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

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

    /**
     * The target for one instance's progress. The same as {@link #required()} except where an instance
     * carries its own, frozen by a migration (1.7.0): a project already under way when a bundled
     * definition's number changed keeps the number it was started with.
     */
    default int requiredFor(SharedObjectiveProgress progress) {
        return required();
    }

    /** Shared amount toward {@link #requiredFor} (clamped). */
    default int current(SharedObjectiveProgress progress) {
        return Math.min(progress.count(), requiredFor(progress));
    }

    default boolean isSatisfied(SharedObjectiveProgress progress) {
        return progress.count() >= requiredFor(progress);
    }

    /**
     * Expanded help for one objective of one instance: what counts, where, the next useful action and
     * why it is blocked, built from the same predicates that grant credit (1.7.0). Shown behind the
     * objective's help toggle, never in the one-line summary. Empty when there is nothing to add.
     */
    default java.util.List<Component> explain(ProjectObjectiveContext context) {
        return java.util.List.of();
    }

    /** A coarse state for text-and-glyph display and operator diagnostics. */
    default ProjectObjectiveStatus status(ProjectObjectiveContext context) {
        if (isSatisfied(context.progress())) {
            return ProjectObjectiveStatus.SATISFIED;
        }
        if (context.level() != null && context.state() != null && !isAvailable(context.level(), context.state())) {
            return ProjectObjectiveStatus.UNAVAILABLE;
        }
        return ProjectObjectiveStatus.IN_PROGRESS;
    }

    /** Missing optional content pauses deadlines as well as objective polling. */
    default boolean isAvailable(net.minecraft.server.level.ServerLevel level,
                                dev.otectus.mcaquests.project.state.ProjectState state) {
        return true;
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

    /**
     * Datapack-load validation hook, mirroring {@code QuestObjective#validate}.
     *
     * <p>Project objectives had none, which made them strictly weaker than the quest objectives they sit
     * beside: the same mistyped structure or empty item target that a quest catches at reload passed
     * silently into a project, where it becomes a phase that can never complete and so a project that can
     * never finish — for every player in the village at once, not just the one who accepted it.
     *
     * <p>Messages should be prefixed {@code "Project '<id>': phase '<key>' objective[<index>] "}. No-op by
     * default, so existing objective types, including add-on ones, are unaffected.
     */
    default void validate(ResourceLocation projectId, String phaseKey, int index, List<String> errors) {
    }
}
