package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.server.level.ServerPlayer;

/** Fired when a quest's objectives first become complete and it is ready to turn in (spec section 28). */
public class QuestReadyEvent extends QuestEvent {

    public QuestReadyEvent(ServerPlayer player, QuestDefinition definition) {
        super(player, null, definition);
    }
}
