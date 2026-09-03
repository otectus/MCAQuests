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

/** Client to server: abandon an active quest (spec section 20). */
public record QuestAbandonC2SPacket(UUID villagerUuid, ResourceLocation questId) implements CustomPacketPayload {

    public static final Type<QuestAbandonC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_abandon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestAbandonC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestAbandonC2SPacket::encode, QuestAbandonC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        buf.writeResourceLocation(this.questId);
    }

    public static QuestAbandonC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestAbandonC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(QuestAbandonC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        QuestManager.abandonFromPacket(player, msg.villagerUuid, msg.questId);
    }
}
