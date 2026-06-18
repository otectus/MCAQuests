package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Fired after a quest is completed and its rewards (items/XP/hearts) have been granted (spec section 28). */
public class QuestCompletedEvent extends QuestEvent {

    public QuestCompletedEvent(ServerPlayer player, @Nullable Entity villager, QuestDefinition definition) {
        super(player, villager, definition);
    }
}
