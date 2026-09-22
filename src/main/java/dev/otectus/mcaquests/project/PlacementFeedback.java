package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Why a block a player just placed did not count for a project, told to that player once, on the action
 * bar (1.7.0).
 *
 * <p>Only a placement that <em>could</em> have counted is explained — the material is one a project asks
 * for — so building a house next to a wall project says nothing. The most actionable reason wins when a
 * block is relevant to several projects, and a player hears at most one line every two seconds, so
 * laying a row of wall outside the area is one message, not sixty.
 */
final class PlacementFeedback {

    /** In the order they are preferred when more than one applies. */
    enum Reason {
        OUTSIDE, NOT_THIS_PHASE, ALREADY_COUNTED, LIMIT, WRONG_DIMENSION
    }

    private static final long MIN_INTERVAL_TICKS = 40L;
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();

    @Nullable
    private Reason reason;
    @Nullable
    private Component message;

    boolean hasReason() {
        return reason != null;
    }

    void offer(Reason candidate, ProjectState state, ProjectDefinition def, int blocksOutside) {
        if (reason != null && reason.ordinal() <= candidate.ordinal()) {
            return;
        }
        Component title = def.displayTitle();
        message = switch (candidate) {
            case OUTSIDE -> Component.translatable("mcaquests.project.place.outside", title, Math.max(1, blocksOutside));
            case ALREADY_COUNTED -> Component.translatable("mcaquests.project.place.already_counted", title);
            case LIMIT -> Component.translatable("mcaquests.project.place.limit", title);
            case WRONG_DIMENSION -> Component.translatable("mcaquests.project.place.dimension", title);
            case NOT_THIS_PHASE -> Component.translatable("mcaquests.project.place.not_this_phase", title);
        };
        reason = candidate;
    }

    /** The material belongs to phase {@code neededPhase}, and the project is in another phase. */
    void offerPhase(ProjectState state, ProjectDefinition def, int neededPhase) {
        if (reason != null && reason.ordinal() <= Reason.NOT_THIS_PHASE.ordinal()) {
            return;
        }
        reason = Reason.NOT_THIS_PHASE;
        message = Component.translatable(neededPhase > state.currentPhase()
                        ? "mcaquests.project.place.phase_later" : "mcaquests.project.place.phase_done",
                def.displayTitle(), neededPhase + 1, state.currentPhase() + 1);
    }

    void send(ServerPlayer player) {
        if (message == null) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_SENT.get(player.getUUID());
        if (last != null && now >= last && now - last < MIN_INTERVAL_TICKS) {
            return;
        }
        if (LAST_SENT.size() > 256) {
            LAST_SENT.clear();
        }
        LAST_SENT.put(player.getUUID(), now);
        player.displayClientMessage(message, true);
    }

    static void clearSessionState() {
        LAST_SENT.clear();
    }
}
