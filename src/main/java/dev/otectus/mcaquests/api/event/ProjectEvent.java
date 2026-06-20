package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraftforge.eventbus.api.Event;

/**
 * Base class for community-project lifecycle events (spec 0.4.0), posted on the Forge event bus so
 * other mods/datapacks can observe project progress. All are server-side and not cancellable.
 */
public abstract class ProjectEvent extends Event {

    private final ProjectDefinition definition;
    private final ProjectState state;

    protected ProjectEvent(ProjectDefinition definition, ProjectState state) {
        this.definition = definition;
        this.state = state;
    }

    public ProjectDefinition definition() {
        return definition;
    }

    public ProjectState state() {
        return state;
    }

    /** Fired after a phase completes and its rewards are distributed. */
    public static final class PhaseAdvanced extends ProjectEvent {
        private final int completedPhase;

        public PhaseAdvanced(ProjectDefinition definition, ProjectState state, int completedPhase) {
            super(definition, state);
            this.completedPhase = completedPhase;
        }

        public int completedPhase() {
            return completedPhase;
        }
    }

    /** Fired when a project reaches its final phase and completes. */
    public static final class Completed extends ProjectEvent {
        public Completed(ProjectDefinition definition, ProjectState state) {
            super(definition, state);
        }
    }

    /** Fired when a project fails (e.g. sponsor loss with FAIL behavior). */
    public static final class Failed extends ProjectEvent {
        public Failed(ProjectDefinition definition, ProjectState state) {
            super(definition, state);
        }
    }
}
