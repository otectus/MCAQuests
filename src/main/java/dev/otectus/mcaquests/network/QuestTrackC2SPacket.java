package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: follow this quest, or stop following anything.
 *
 * <p>Carries the giver's UUID alongside the quest id for the same reason
 * {@link QuestAbandonFromLogC2SPacket} does — the same quest can be active from two different
 * villagers, so the id alone does not name one. And as there, the giver need not exist: a player can
 * follow a quest whose villager is dead or three thousand blocks away, which is precisely when a
 * marker is worth having.
 *
 * <p>An empty payload means "stop following", sent when the player clicks the pin on the quest they
 * are already following. Identifiers only; the server re-resolves against its own state and ignores a
 * quest the player does not actually hold (never trust the client — spec section 20/26).
 */
public record QuestTrackC2SPacket(Optional<UUID> villagerUuid, Optional<ResourceLocation> questId) {

    /** Stop following anything. */
    public static QuestTrackC2SPacket none() {
        return new QuestTrackC2SPacket(Optional.empty(), Optional.empty());
    }

    public static QuestTrackC2SPacket of(UUID villagerUuid, ResourceLocation questId) {
        return new QuestTrackC2SPacket(Optional.of(villagerUuid), Optional.of(questId));
    }

    public static void encode(QuestTrackC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeOptional(msg.villagerUuid, FriendlyByteBuf::writeUUID);
        buf.writeOptional(msg.questId, FriendlyByteBuf::writeResourceLocation);
    }

    public static QuestTrackC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestTrackC2SPacket(buf.readOptional(FriendlyByteBuf::readUUID),
                buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }

    public static void handle(QuestTrackC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (msg.villagerUuid.isPresent() && msg.questId.isPresent()) {
                QuestManager.track(player, msg.villagerUuid.get(), msg.questId.get());
            } else {
                QuestManager.track(player, null, null);
            }
        });
        context.setPacketHandled(true);
    }
}
