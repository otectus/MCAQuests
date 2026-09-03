package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestManager;
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

    public static final Type<QuestDecisionC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_decision"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestDecisionC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestDecisionC2SPacket::encode, QuestDecisionC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        buf.writeResourceLocation(this.questId);
        buf.writeBoolean(this.accept);
    }

    public static QuestDecisionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestDecisionC2SPacket(buf.readUUID(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(QuestDecisionC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        QuestManager.acceptFromPacket(player, msg.villagerUuid, msg.questId, msg.accept);
    }
}
