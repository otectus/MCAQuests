package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.FailureSpec;
import java.util.OptionalLong;

/** The clock and retry rules shared by automatic and sponsor-triggered project failures. */
public final class ProjectFailure {
    private ProjectFailure() { }

    public static boolean deadlinePassed(ProjectState state, FailureSpec failure, long gameTime, long dayTime) {
        OptionalLong deadline = failure.deadlineGameTime(state.startGameTime(), state.startDayTime(), gameTime, dayTime);
        return deadline.isPresent() && gameTime - state.suspendedTicks() >= deadline.getAsLong();
    }

    public static long retryAt(FailureSpec failure, long now) {
        if (failure.blockRetry()) { return Long.MAX_VALUE; }
        long delay = failure.retryAfterTicks().orElse(0);
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }
}
