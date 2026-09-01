package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fired after a player turns an offer down (1.4.3).
 *
 * <p>Declining is free — no hearts, no reputation, no failure — so this is purely a signal. A dialogue
 * add-on can use it to have the villager react; a pack can branch on it through the
 * {@code mcaquests:quest_declined} condition instead.
 *
 * <p>The villager is always known here, unlike {@link QuestAbandonedEvent}: you can only decline an offer
 * you are standing in front of.
 */
public class QuestDeclinedEvent extends QuestEvent {

    public QuestDeclinedEvent(ServerPlayer player, Entity villager, QuestDefinition definition) {
        super(player, villager, definition);
    }
}
