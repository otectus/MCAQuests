package dev.otectus.mcaquests.quest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A client-facing snapshot of one active quest, synced for the quest log + HUD tracker (spec §21).
 * Built server-side from {@code ActiveQuest} + its definition, including objective progress text.
 */
public record QuestLogEntry(ResourceLocation questId, Component title, List<Component> objectives, boolean ready) {

    public static void encode(FriendlyByteBuf buf, QuestLogEntry entry) {
        buf.writeResourceLocation(entry.questId);
        buf.writeComponent(entry.title);
        buf.writeCollection(entry.objectives, FriendlyByteBuf::writeComponent);
        buf.writeBoolean(entry.ready);
    }

    public static QuestLogEntry decode(FriendlyByteBuf buf) {
        return new QuestLogEntry(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readBoolean());
    }
}
