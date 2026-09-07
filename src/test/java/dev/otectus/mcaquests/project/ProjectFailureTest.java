package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.quest.FailureSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

class ProjectFailureTest {
    private static ProjectState state() {
        ProjectState state = new ProjectState(new ResourceLocation("test", "project"), ProjectScope.VILLAGE,
                "v:1", new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO, OptionalInt.of(1), 100, 1);
        state.setStartDayTime(12000);
        return state;
    }

    private static FailureSpec failure(Optional<Integer> ticks, Optional<Integer> clock,
                                       Optional<Integer> retry, boolean blocked) {
        return new FailureSpec(ticks, clock, Optional.empty(), false, 0, retry, blocked);
    }

    @Test void elapsedDeadlineSurvivesReloadAndExcludesPausedTime() {
        ProjectState state = state();
        FailureSpec failure = failure(Optional.of(100), Optional.empty(), Optional.empty(), false);
        state.sampleClock(100, false);
        state.sampleClock(150, true);
        state.sampleClock(200, true);
        ProjectState loaded = ProjectState.load(state.save());
        assertEquals(100, loaded.suspendedTicks());
        assertFalse(ProjectFailure.deadlinePassed(loaded, failure, 299, 12199));
        assertTrue(ProjectFailure.deadlinePassed(loaded, failure, 300, 12200));
    }

    @Test void sleepingAdvancesWorldClockDeadlineWithoutAdvancingElapsedDeadline() {
        ProjectState state = state();
        assertTrue(ProjectFailure.deadlinePassed(state,
                failure(Optional.empty(), Optional.of(23000), Optional.empty(), false), 120, 24000));
        assertFalse(ProjectFailure.deadlinePassed(state,
                failure(Optional.of(12000), Optional.empty(), Optional.empty(), false), 120, 24000));
    }

    @Test void failedProjectsOnlyReopenAfterTheirAuthoredRetry() {
        ProjectState state = state();
        state.setStatus(ProjectStatus.FAILED);
        assertFalse(state.canRetry(99999), "old failed saves retain the historical terminal state");
        state.allowRetryAt(ProjectFailure.retryAt(
                failure(Optional.of(100), Optional.empty(), Optional.of(200), false), 300));
        assertFalse(state.canRetry(499));
        assertTrue(ProjectState.load(state.save()).canRetry(500));
        state.allowRetryAt(ProjectFailure.retryAt(
                failure(Optional.of(100), Optional.empty(), Optional.of(200), true), 300));
        assertFalse(state.canRetry(Long.MAX_VALUE));
    }

    @Test void newDatapackObjectiveCanBeTrackedWithoutLosingOldProgress() {
        ProjectState state = state();
        state.progress(0).add(7);
        state.progress(1).add(2);
        assertEquals(7, state.progress(0).count());
        assertEquals(2, ProjectState.load(state.save()).progress(1).count());
    }

    @Test void legacyProjectsGetAFreshDeadlineWithoutLosingContributions() {
        ProjectState legacy = new ProjectState(new ResourceLocation("test", "project"), ProjectScope.VILLAGE,
                "v:1", new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO, OptionalInt.of(1), 100, 1);
        legacy.progress(0).add(7);
        legacy.initializeFailureClock(100000, 24000);
        assertEquals(7, legacy.progress(0).count());
        FailureSpec rule = failure(Optional.of(100), Optional.empty(), Optional.empty(), false);
        assertFalse(ProjectFailure.deadlinePassed(legacy, rule, 100099, 24099));
        assertTrue(ProjectFailure.deadlinePassed(legacy, rule, 100100, 24100));
    }
}
