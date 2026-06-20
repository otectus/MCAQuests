package dev.otectus.mcaquests.quest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * A client-facing snapshot of one active quest, synced for the quest log + HUD tracker (spec §21).
 * Built server-side from {@code ActiveQuest} + its definition, including objective progress text.
 *
 * <p>{@code deadlineGameTime} is the absolute world game-time this quest expires at when it has a
 * {@code failure} time deadline, or empty otherwise; the HUD turns it into a live countdown by
 * comparing against the client's synced game-time. Weather/giver-death failures carry no countdown.
 */
public record QuestLogEntry(ResourceLocation questId, Component title, Component giverName,
                            Component chainLabel, List<Component> objectives, boolean ready,
                            OptionalLong deadlineGameTime) {

    public static void encode(FriendlyByteBuf buf, QuestLogEntry entry) {
        buf.writeResourceLocation(entry.questId);
        buf.writeComponent(entry.title);
        buf.writeComponent(entry.giverName);
        buf.writeComponent(entry.chainLabel);
        buf.writeCollection(entry.objectives, FriendlyByteBuf::writeComponent);
        buf.writeBoolean(entry.ready);
        buf.writeBoolean(entry.deadlineGameTime.isPresent());
        if (entry.deadlineGameTime.isPresent()) {
            buf.writeVarLong(entry.deadlineGameTime.getAsLong());
        }
    }

    public static QuestLogEntry decode(FriendlyByteBuf buf) {
        return new QuestLogEntry(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readBoolean(),
                buf.readBoolean() ? OptionalLong.of(buf.readVarLong()) : OptionalLong.empty());
    }
}
