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

/** Client to server: accept (or decline) the offered quest (spec section 20). */
public record QuestDecisionC2SPacket(UUID villagerUuid, ResourceLocation questId, boolean accept)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestDecisionC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_decision"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestDecisionC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestDecisionC2SPacket::encode, QuestDecisionC2SPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(QuestDecisionC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
        buf.writeBoolean(msg.accept);
    }

    public static QuestDecisionC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestDecisionC2SPacket(buf.readUUID(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(QuestDecisionC2SPacket msg, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuestManager.acceptFromPacket(player, msg.villagerUuid, msg.questId, msg.accept);
        }
    }
}
