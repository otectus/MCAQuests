package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Fired when an active quest fails (e.g. its giver died with {@code failQuestIfGiverDies}) — spec section 28. */
public class QuestFailedEvent extends QuestEvent {

    /** Why the quest failed. */
    public enum Reason {
        GIVER_DIED
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
