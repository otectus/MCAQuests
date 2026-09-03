package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.NetComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 *
 * <p><b>Where a quest is sending the player is not here.</b> It used to be, as a {@code TargetHint}
 * of a name and a {@code BlockPos} — with no dimension, because "an arrow across dimensions would be
 * a lie" — and it could only ever name a villager, so a quest about an ancient city had nothing to
 * put in it. That is {@code GuidanceTarget}'s job, it does it for places as well as people and with
 * the dimension attached, and since 1.5.0 the guidance packet carries one per quest rather than one
 * per player. Keeping both would have been two answers to one question, drifting apart.
 *
 * <p>{@code tracked} is whether this is the quest the world marker and the villager outline are about.
 * Exactly one entry can have it, and none need to. It is here rather than on the guidance packet
 * because the quest log has to draw the pin on the right row, which is a question about the list and
 * not about the marker.
 */
public record QuestLogEntry(ResourceLocation questId, UUID villagerUuid, Component title, Component giverName,
                            Component chainLabel, List<CardObjective> objectives, boolean ready,
                            boolean suspended, boolean tracked, OptionalLong deadlineGameTime,
                            List<Component> townsteadContext) {

    public static void encode(RegistryFriendlyByteBuf buf, QuestLogEntry entry) {
        buf.writeResourceLocation(entry.questId);
        buf.writeUUID(entry.villagerUuid);
        NetComponents.write(buf, entry.title);
        NetComponents.write(buf, entry.giverName);
        NetComponents.write(buf, entry.chainLabel);
        buf.writeCollection(entry.objectives, (b, v) -> CardObjective.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeBoolean(entry.ready);
        buf.writeBoolean(entry.suspended);
        buf.writeBoolean(entry.tracked);
        buf.writeBoolean(entry.deadlineGameTime.isPresent());
        if (entry.deadlineGameTime.isPresent()) {
            buf.writeVarLong(entry.deadlineGameTime.getAsLong());
        }
        buf.writeCollection(entry.townsteadContext, NetComponents::write);
    }

    public static QuestLogEntry decode(RegistryFriendlyByteBuf buf) {
        return new QuestLogEntry(
                buf.readResourceLocation(),
                buf.readUUID(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readCollection(ArrayList::new, b -> CardObjective.decode((RegistryFriendlyByteBuf) b)),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean() ? OptionalLong.of(buf.readVarLong()) : OptionalLong.empty(),
                buf.readCollection(ArrayList::new, NetComponents::read));
    }
}
