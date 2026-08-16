package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client to server: abandon an active quest (spec section 20). */
public record QuestAbandonC2SPacket(UUID villagerUuid, ResourceLocation questId) {

    public static void encode(QuestAbandonC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
    }

    public static QuestAbandonC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestAbandonC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(QuestAbandonC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                QuestManager.abandonFromPacket(player, msg.villagerUuid, msg.questId);
            }
        });
        context.setPacketHandled(true);
    }
}
