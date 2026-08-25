package dev.otectus.mcaquests.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * <p>{@code target} is the villager the quest wants the player to find, with the position the HUD turns
 * into a live distance and compass bearing. Empty when nothing is findable (the target is unloaded and has
 * no known home, or is in another dimension), in which case the HUD renders exactly as it always did.
 */
public record QuestLogEntry(ResourceLocation questId, UUID villagerUuid, Component title, Component giverName,
                            Component chainLabel, List<Component> objectives, boolean ready,
                            boolean suspended, OptionalLong deadlineGameTime,
                            Optional<TargetHint> target) {

    /**
     * Where the quest wants the player to go: a villager's name and their position. The position is a live
     * one when the villager is loaded, otherwise their last known home, so the arrow still points somewhere
     * useful for a target far outside render distance.
     */
    public record TargetHint(Component name, BlockPos pos, boolean lastKnown) {

        static void encode(FriendlyByteBuf buf, TargetHint hint) {
            buf.writeComponent(hint.name);
            buf.writeBlockPos(hint.pos); // one long: VarInt is unsigned-biased, so negative coords cost 5B each
            buf.writeBoolean(hint.lastKnown);
        }

        static TargetHint decode(FriendlyByteBuf buf) {
            return new TargetHint(buf.readComponent(), buf.readBlockPos(), buf.readBoolean());
        }
    }

    public static void encode(FriendlyByteBuf buf, QuestLogEntry entry) {
        buf.writeResourceLocation(entry.questId);
        buf.writeUUID(entry.villagerUuid);
        buf.writeComponent(entry.title);
        buf.writeComponent(entry.giverName);
        buf.writeComponent(entry.chainLabel);
        buf.writeCollection(entry.objectives, FriendlyByteBuf::writeComponent);
        buf.writeBoolean(entry.ready);
        buf.writeBoolean(entry.suspended);
        buf.writeBoolean(entry.deadlineGameTime.isPresent());
        if (entry.deadlineGameTime.isPresent()) {
            buf.writeVarLong(entry.deadlineGameTime.getAsLong());
        }
        buf.writeOptional(entry.target, TargetHint::encode);
    }

    public static QuestLogEntry decode(FriendlyByteBuf buf) {
        return new QuestLogEntry(
                buf.readResourceLocation(),
                buf.readUUID(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean() ? OptionalLong.of(buf.readVarLong()) : OptionalLong.empty(),
                buf.readOptional(TargetHint::decode));
    }
}
