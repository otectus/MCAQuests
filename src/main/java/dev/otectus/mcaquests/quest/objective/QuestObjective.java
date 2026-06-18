package dev.otectus.mcaquests.quest.objective;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A single requirement a player must satisfy to complete a quest (spec section 14). Registry-driven
 * via {@link ObjectiveTypes} so add-ons can register new types.
 *
 * <p>Two flavours: <em>possession</em> objectives (e.g. {@code item_delivery}) compute completion
 * from the player's live state; <em>accumulation</em> objectives (kill, break — Phase 2) advance a
 * counter in {@link ObjectiveProgress} via Forge events. {@link #isEventDriven()} distinguishes them.
 */
public interface QuestObjective {

    QuestObjectiveType<?> type();

    /** Human-readable one-line summary for the quest card, e.g. "Deliver 24 Wheat". */
    Component describe();

    /** Target amount (for progress display). */
    int required();

    /** Current amount toward {@link #required()} for this player/progress (clamped to required). */
    int current(ServerPlayer player, ObjectiveProgress progress);

    /** True when this objective is fully satisfied. */
    boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress);

    /** Apply any consumption/side effects at turn-in, after every objective has been validated. */
    default void consumeOnTurnIn(ServerPlayer player, ObjectiveProgress progress) {
    }

    /** True if progress accumulates via game events rather than a live state check. */
    default boolean isEventDriven() {
        return false;
    }
}
