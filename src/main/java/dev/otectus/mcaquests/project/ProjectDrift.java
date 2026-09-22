package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.objective.ProjectObjectiveTypes;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.DefinitionFingerprint;
import dev.otectus.mcaquests.quest.QuestDrift;

import java.util.List;

/**
 * Whether a datapack edit has changed the objectives of an instance's current phase since the phase
 * opened (1.7.0) — the project counterpart of {@link QuestDrift}. Shared progress is saved by position,
 * so a phase whose objectives were inserted, removed or reordered is paused, clock included, instead of
 * reinterpreting what the village already did; {@code /mcaquests project instance ... rebase} accepts the
 * new phase. Appending objectives is not drift. An instance from before 1.7.0 records the phase it finds.
 */
public final class ProjectDrift {

    private ProjectDrift() {
    }

    public static List<String> fingerprints(ProjectDefinition def, int phase) {
        return DefinitionFingerprint.ofEach(ProjectObjectiveTypes.CODEC, def.phase(phase).objectives());
    }

    /** Records the current phase as it opens. */
    public static void capture(ProjectState state, ProjectDefinition def) {
        if (state.currentPhase() >= 0 && state.currentPhase() < def.phaseCount()) {
            state.setPhaseFingerprints(fingerprints(def, state.currentPhase()));
        }
    }

    public static boolean drifted(ProjectState state, ProjectDefinition def) {
        if (state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
            return false;
        }
        List<String> opened = state.phaseFingerprints();
        List<String> current = fingerprints(def, state.currentPhase());
        if (opened.isEmpty()) {
            state.setPhaseFingerprints(current);
            return false;
        }
        if (!QuestDrift.compatible(opened, current)) {
            return true;
        }
        if (current.size() > opened.size()) {
            state.setPhaseFingerprints(current);
        }
        return false;
    }
}
