package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Fired after a player abandons an active quest (spec section 28). */
public class QuestAbandonedEvent extends QuestEvent {

    public QuestAbandonedEvent(ServerPlayer player, @Nullable Entity villager, QuestDefinition definition) {
        super(player, villager, definition);
    }
}
