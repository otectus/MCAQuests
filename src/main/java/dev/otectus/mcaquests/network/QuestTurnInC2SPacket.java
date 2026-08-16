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

import java.util.UUID;

/** Client to server: attempt to complete an active quest (spec section 20). Fully validated server-side. */
public record QuestTurnInC2SPacket(UUID villagerUuid, ResourceLocation questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestTurnInC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_turn_in"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTurnInC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestTurnInC2SPacket::encode, QuestTurnInC2SPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(QuestTurnInC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
    }

    public static QuestTurnInC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestTurnInC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(QuestTurnInC2SPacket msg, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuestManager.turnInFromPacket(player, msg.villagerUuid, msg.questId);
        }
    }
}
