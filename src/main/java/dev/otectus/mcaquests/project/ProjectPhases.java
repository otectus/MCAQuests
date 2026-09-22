package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.project.objective.PollingProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.TownsteadSpiritProjectObjective;
import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The one way a project instance begins and a phase becomes current (1.7.0).
 *
 * <p>Before this, a new instance, a phase advance, a seeded follow-up and {@code /mcaquests project
 * advance} each set the phase index on their own, and anything a phase needed to remember about its
 * starting point was captured later, on the first periodic poll that happened to succeed. A Townstead
 * spirit objective therefore froze its baseline whenever it was first polled: spirit gained between the
 * phase opening and that poll became part of the baseline and was asked for a second time. Every path
 * now comes through here, and every reading is taken at the boundary it describes.
 *
 * <p>What is kept, per instance, in {@link ProjectState#extra()}:
 * <ul>
 *   <li>{@code spirit_start}: the village's spirit when the project began — total and per spirit —
 *       for objectives measured from the project's start ({@code "baseline": "project"});</li>
 *   <li>{@code spirit_start_source}: {@code project_start}, or {@code late} when the first reading
 *       could only be taken after the start, or {@code operator} after a repair;</li>
 *   <li>{@code spirit_start_pending}: set while that reading could not be taken.</li>
 * </ul>
 * An instance created before 1.7.0 has none of these. Nothing pretends to reconstruct its start: its
 * project-measured objectives fall back to what they did before, measured from the phase, and an
 * operator can repair one explicitly.
 */
public final class ProjectPhases {

    public static final String K_SPIRIT_START = "spirit_start";
    public static final String K_SPIRIT_START_SOURCE = "spirit_start_source";
    public static final String K_SPIRIT_START_PENDING = "spirit_start_pending";

    private ProjectPhases() {
    }

    /**
     * A brand-new instance: take the project-level readings, then initialise phase 0. The instance's
     * progress list is already sized for phase 0 by its constructor.
     */
    public static void begin(@Nullable MinecraftServer server, @Nullable ServerLevel level, ProjectDefinition def,
                             ProjectState state) {
        if (needsSpiritSnapshot(def)) {
            if (!captureSpiritSnapshot(level, state, "project_start")) {
                state.extra().putBoolean(K_SPIRIT_START_PENDING, true);
            }
        }
        initializePhase(server, level, def, state);
    }

    /** Makes {@code phase} current with fresh progress, then lets its objectives take their readings. */
    public static void enter(@Nullable MinecraftServer server, @Nullable ServerLevel level, ProjectDefinition def,
                             ProjectState state, int phase) {
        state.enterPhase(phase, def.phase(phase).objectives().size());
        initializePhase(server, level, def, state);
    }

    private static void initializePhase(@Nullable MinecraftServer server, @Nullable ServerLevel level,
                                        ProjectDefinition def, ProjectState state) {
        int phase = state.currentPhase();
        if (phase < 0 || phase >= def.phaseCount()) {
            return;
        }
        var objectives = def.phase(phase).objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i) instanceof PollingProjectObjective polling) {
                polling.onPhaseEntered(server, level, def, state, i, state.progress(i));
            }
        }
    }

    /**
     * Retries every reading this instance is waiting for. Returns true when persistent state changed.
     * Runs at the top of each sweep, before availability is judged.
     */
    public static boolean resolvePending(MinecraftServer server, @Nullable ServerLevel level, ProjectDefinition def,
                                         ProjectState state) {
        boolean changed = false;
        if (state.extra().getBoolean(K_SPIRIT_START_PENDING) && captureSpiritSnapshot(level, state, "late")) {
            state.extra().remove(K_SPIRIT_START_PENDING);
            changed = true;
        }
        int phase = state.currentPhase();
        if (level != null && phase >= 0 && phase < def.phaseCount()) {
            var objectives = def.phase(phase).objectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (objectives.get(i) instanceof PollingProjectObjective polling
                        && polling.resolvePending(server, level, def, state, state.progress(i))) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** True when any phase measures spirit from the project's start. */
    public static boolean needsSpiritSnapshot(ProjectDefinition def) {
        for (ProjectPhase phase : def.phases()) {
            for (ProjectObjective objective : phase.objectives()) {
                if (objective instanceof TownsteadSpiritProjectObjective spirit
                        && spirit.baseline() == TownsteadSpiritProjectObjective.Baseline.PROJECT) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How a village's spirit is read; replaced only by tests, which have no level to read from. */
    @FunctionalInterface
    public interface SpiritReader {
        Optional<TownsteadSpiritView> read(@Nullable ServerLevel level, int villageId);
    }

    private static final SpiritReader LIVE_READER = (level, villageId) ->
            level == null ? Optional.empty() : new TownsteadEvaluation().spirit(level, villageId);
    private static volatile SpiritReader reader = LIVE_READER;

    /** Test seam; {@code null} restores the live reader. */
    public static void setSpiritReaderForTest(@Nullable SpiritReader replacement) {
        reader = replacement == null ? LIVE_READER : replacement;
    }

    /** The village's current spirit, or empty when it cannot be read right now. */
    public static Optional<TownsteadSpiritView> readSpirit(@Nullable ServerLevel level, ProjectState state) {
        if (state.villageId().isEmpty() || !TownsteadBridge.Holder.get().has(TownsteadCapability.READ_SPIRIT)) {
            return Optional.empty();
        }
        return reader.read(level, state.villageId().getAsInt());
    }

    /** Points for one spirit, or the village total when {@code spirit} is empty. */
    public static int pointsOf(TownsteadSpiritView view, Optional<String> spirit) {
        return spirit.map(view::pointsFor).orElseGet(view::total);
    }

    /** Records the project-level spirit snapshot. Returns false when spirit cannot be read. */
    public static boolean captureSpiritSnapshot(@Nullable ServerLevel level, ProjectState state, String source) {
        Optional<TownsteadSpiritView> view = readSpirit(level, state);
        if (view.isEmpty()) {
            return false;
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("total", view.get().total());
        CompoundTag points = new CompoundTag();
        view.get().perSpirit().forEach(points::putInt);
        snapshot.put("points", points);
        state.extra().put(K_SPIRIT_START, snapshot);
        state.extra().putString(K_SPIRIT_START_SOURCE, source);
        return true;
    }

    /** The project-start reading for one spirit (or the total), when this instance has one. */
    public static OptionalInt spiritAtStart(ProjectState state, Optional<String> spirit) {
        if (!state.extra().contains(K_SPIRIT_START)) {
            return OptionalInt.empty();
        }
        CompoundTag snapshot = state.extra().getCompound(K_SPIRIT_START);
        return spirit.map(id -> OptionalInt.of(snapshot.getCompound("points").getInt(id)))
                .orElseGet(() -> OptionalInt.of(snapshot.getInt("total")));
    }

    public static boolean spiritAtStartPending(ProjectState state) {
        return state.extra().getBoolean(K_SPIRIT_START_PENDING);
    }

    /** {@code project_start}, {@code late} or {@code operator}; empty for an instance from before 1.7.0. */
    public static Optional<String> spiritAtStartSource(ProjectState state) {
        return state.extra().contains(K_SPIRIT_START_SOURCE)
                ? Optional.of(state.extra().getString(K_SPIRIT_START_SOURCE)) : Optional.empty();
    }
}
