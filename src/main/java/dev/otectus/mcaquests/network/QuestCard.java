package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One quest as shown in the menu — an offer or the active quest (spec sections 8, 21). The screen
 * renders these; the {@code questId} drives the Accept/Decline/Complete/Abandon C2S packets.
 *
 * <p>Three things arrived with the interface rewrite, and all three are things the server already
 * knew and simply never said:
 *
 * <ul>
 *   <li>{@code objectives} became {@link CardObjective}s rather than sentences with the counts
 *       written into them, so the client can draw a bar and a state icon instead of reading a number
 *       out of a string it cannot parse.</li>
 *   <li>{@code rewardIcons} are preview stacks from {@code QuestReward.previewIcons()}. The reward
 *       text was always there; the icons are what make "an emerald or a diamond?" a glance rather
 *       than a read.</li>
 *   <li>{@code difficulty} is the band the quest has declared since difficulty existed. It set the
 *       currency reward and was shown to nobody.</li>
 * </ul>
 *
 * @param difficulty {@code "easy"}, {@code "medium"}, {@code "hard"}, or empty when the quest
 *                   declares none — most do not, and an absent band means "no badge" rather than
 *                   "easy"
 */
public record QuestCard(ResourceLocation questId, Component title, Component chainLabel, Component dialogue,
                        List<CardObjective> objectives, List<Component> rewards,
                        List<ItemStack> rewardIcons, String difficulty) {

    public static void encode(FriendlyByteBuf buf, QuestCard card) {
        buf.writeResourceLocation(card.questId);
        buf.writeComponent(card.title);
        buf.writeComponent(card.chainLabel);
        buf.writeComponent(card.dialogue);
        buf.writeCollection(card.objectives, CardObjective::encode);
        buf.writeCollection(card.rewards, FriendlyByteBuf::writeComponent);
        buf.writeCollection(card.rewardIcons, FriendlyByteBuf::writeItem);
        buf.writeUtf(card.difficulty);
    }

    public static QuestCard decode(FriendlyByteBuf buf) {
        return new QuestCard(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, CardObjective::decode),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readItem),
                buf.readUtf());
    }
}
