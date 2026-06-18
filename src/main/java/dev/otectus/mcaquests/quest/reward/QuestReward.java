package dev.otectus.mcaquests.quest.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Something granted on quest completion (spec section 15). Registry-driven via {@link RewardTypes}.
 * <b>All rewards are applied server-side only</b>, after objective items are consumed and every
 * validation has passed.
 */
public interface QuestReward {

    QuestRewardType<?> type();

    /** One-line summary for the quest card, e.g. "3x Emerald" or "+25 hearts". */
    Component describe();

    /**
     * Deliver the reward. {@code villager} is the quest giver (may be null if it has gone missing);
     * hearts rewards require it.
     */
    void grant(ServerPlayer player, @Nullable Entity villager);
}
