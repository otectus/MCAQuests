package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client to server: accept (or decline) the offered quest (spec section 20). */
public record QuestDecisionC2SPacket(UUID villagerUuid, ResourceLocation questId, boolean accept) {

    public static void encode(QuestDecisionC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
        buf.writeBoolean(msg.accept);
    }

    public static QuestDecisionC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestDecisionC2SPacket(buf.readUUID(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(QuestDecisionC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                QuestManager.acceptFromPacket(player, msg.villagerUuid, msg.questId, msg.accept);
            }
        });
        context.setPacketHandled(true);
    }
}
