package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Fired when an active quest fails (e.g. its giver died with {@code failQuestIfGiverDies}) — spec section 28. */
public class QuestFailedEvent extends QuestEvent {

    /** Why the quest failed. */
    public enum Reason {
        GIVER_DIED,
        /** A relative {@code failure.deadline_ticks} budget elapsed. */
        TIME_LIMIT,
        /** A {@code failure.deadline_time} time-of-day window passed before turn-in. */
        TIME_WINDOW,
        /** A {@code failure.require_weather} weather condition stopped holding. */
        WEATHER,
        /** A {@code protect_entity} objective's target died before the protection window elapsed. */
        PROTECT_TARGET_DIED,
        /** A staged {@code escort_entity} objective's escortee died after the escort began (player engaged). */
        ESCORT_TARGET_DIED,
        /** The situation this quest was accepted from closed (deadline expired or the condition resolved) — 0.8.0. */
        SITUATION_CLOSED
    }

    private final Reason reason;

    public QuestFailedEvent(ServerPlayer player, @Nullable Entity villager, QuestDefinition definition, Reason reason) {
        super(player, villager, definition);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
