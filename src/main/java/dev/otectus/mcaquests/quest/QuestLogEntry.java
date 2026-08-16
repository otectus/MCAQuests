package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.network.NetComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * A client-facing snapshot of one active quest, synced for the quest log + HUD tracker (spec §21).
 * Built server-side from {@code ActiveQuest} + its definition, including objective progress text.
 *
 * <p>{@code deadlineGameTime} is the absolute world game-time this quest expires at when it has a
 * {@code failure} time deadline, or empty otherwise; the HUD turns it into a live countdown by
 * comparing against the client's synced game-time. Weather/giver-death failures carry no countdown.
 *
 * <p>{@code villagerUuid} identifies the giver so the log's Abandon button can name the exact
 * active quest to drop — the same {@code questId} may be active from two different villagers. It
 * is the giver's stored identity, not a live entity, so it stays valid once the giver is gone.
 */
public record QuestLogEntry(ResourceLocation questId, UUID villagerUuid, Component title, Component giverName,
                            Component chainLabel, List<Component> objectives, boolean ready,
                            OptionalLong deadlineGameTime) {

    public static void encode(FriendlyByteBuf buf, QuestLogEntry entry) {
        buf.writeResourceLocation(entry.questId);
        buf.writeUUID(entry.villagerUuid);
        NetComponents.write(buf, entry.title);
        NetComponents.write(buf, entry.giverName);
        NetComponents.write(buf, entry.chainLabel);
        buf.writeCollection(entry.objectives, NetComponents::write);
        buf.writeBoolean(entry.ready);
        buf.writeBoolean(entry.deadlineGameTime.isPresent());
        if (entry.deadlineGameTime.isPresent()) {
            buf.writeVarLong(entry.deadlineGameTime.getAsLong());
        }
    }

    public static QuestLogEntry decode(FriendlyByteBuf buf) {
        return new QuestLogEntry(
                buf.readResourceLocation(),
                buf.readUUID(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readCollection(ArrayList::new, NetComponents::read),
                buf.readBoolean(),
                buf.readBoolean() ? OptionalLong.of(buf.readVarLong()) : OptionalLong.empty());
    }
}
