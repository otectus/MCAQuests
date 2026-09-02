package dev.otectus.mcaquests.quest.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

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
     * Item stacks the quest card may draw as icons beside {@link #describe()}, or an empty list for a
     * reward with nothing to show.
     *
     * <p>Purely cosmetic: this is a <em>preview</em>, never the thing that is granted. {@link #grant}
     * remains the only place a reward is delivered, and a reward that returns a misleading stack here
     * misleads a player without giving them anything.
     *
     * <p>Defaults to empty, so existing reward types — including those registered by add-ons — are
     * unaffected and keep compiling. A reward that grants no item (hearts, reputation, a title) is
     * expected to leave it that way; the card falls back to its text line.
     */
    default List<ItemStack> previewIcons() {
        return List.of();
    }

    /**
     * Deliver the reward. {@code villager} is the quest giver (may be null if it has gone missing);
     * hearts rewards require it.
     */
    void grant(ServerPlayer player, @Nullable Entity villager);
}
