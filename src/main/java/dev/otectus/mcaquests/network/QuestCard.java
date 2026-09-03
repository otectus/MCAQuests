package dev.otectus.mcaquests.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public static void encode(RegistryFriendlyByteBuf buf, QuestCard card) {
        buf.writeResourceLocation(card.questId);
        NetComponents.write(buf, card.title);
        NetComponents.write(buf, card.chainLabel);
        NetComponents.write(buf, card.dialogue);
        buf.writeCollection(card.objectives, (b, v) -> CardObjective.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeCollection(card.rewards, NetComponents::write);
        buf.writeCollection(card.rewardIcons,
                (b, stack) -> ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, stack));
        buf.writeUtf(card.difficulty);
    }

    public static QuestCard decode(RegistryFriendlyByteBuf buf) {
        return new QuestCard(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readCollection(ArrayList::new, b -> CardObjective.decode((RegistryFriendlyByteBuf) b)),
                buf.readCollection(ArrayList::new, NetComponents::read),
                buf.readCollection(ArrayList::new,
                        b -> ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) b)),
                buf.readUtf());
    }
}
