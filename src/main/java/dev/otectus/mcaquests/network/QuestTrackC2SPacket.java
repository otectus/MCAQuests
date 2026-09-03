package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

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
public record QuestTrackC2SPacket(Optional<UUID> villagerUuid, Optional<ResourceLocation> questId)
        implements CustomPacketPayload {

    public static final Type<QuestTrackC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestTrackC2SPacket::encode, QuestTrackC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Stop following anything. */
    public static QuestTrackC2SPacket none() {
        return new QuestTrackC2SPacket(Optional.empty(), Optional.empty());
    }

    public static QuestTrackC2SPacket of(UUID villagerUuid, ResourceLocation questId) {
        return new QuestTrackC2SPacket(Optional.of(villagerUuid), Optional.of(questId));
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        // 1.21 added static ByteBuf overloads of read/writeUUID, so the method reference is
        // ambiguous; the lambdas below call exactly the same instance methods.
        buf.writeOptional(this.villagerUuid, (FriendlyByteBuf b, UUID uuid) -> b.writeUUID(uuid));
        buf.writeOptional(this.questId, FriendlyByteBuf::writeResourceLocation);
    }

    public static QuestTrackC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestTrackC2SPacket(buf.readOptional((FriendlyByteBuf b) -> b.readUUID()),
                buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }

    public static void handle(QuestTrackC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (msg.villagerUuid.isPresent() && msg.questId.isPresent()) {
            QuestManager.track(player, msg.villagerUuid.get(), msg.questId.get());
        } else {
            QuestManager.track(player, null, null);
        }
    }
}
