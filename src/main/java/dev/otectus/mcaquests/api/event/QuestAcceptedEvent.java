package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Fired after a player accepts a quest from a villager (spec section 28). */
public class QuestAcceptedEvent extends QuestEvent {

    public QuestAcceptedEvent(ServerPlayer player, Entity villager, QuestDefinition definition) {
        super(player, villager, definition);
    }
}
