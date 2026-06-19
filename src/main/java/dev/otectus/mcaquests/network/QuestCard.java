package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One quest as shown in the menu — an offer or the active quest (spec sections 8, 21). The screen
 * renders these; the {@code questId} drives the Accept/Decline/Complete/Abandon C2S packets.
 */
public record QuestCard(ResourceLocation questId, Component title, Component chainLabel, Component dialogue,
                        List<Component> objectives, List<Component> rewards) {

    public static void encode(FriendlyByteBuf buf, QuestCard card) {
        buf.writeResourceLocation(card.questId);
        buf.writeComponent(card.title);
        buf.writeComponent(card.chainLabel);
        buf.writeComponent(card.dialogue);
        buf.writeCollection(card.objectives, FriendlyByteBuf::writeComponent);
        buf.writeCollection(card.rewards, FriendlyByteBuf::writeComponent);
    }

    public static QuestCard decode(FriendlyByteBuf buf) {
        return new QuestCard(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent));
    }
}
