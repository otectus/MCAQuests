package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: "I clicked the Quests button on this villager." Carries only the villager UUID;
 * the server re-resolves and re-validates everything (never trust the client — spec section 20/26).
 */
public record OpenQuestMenuC2SPacket(UUID villagerUuid) {

    public static void encode(OpenQuestMenuC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
    }

    public static OpenQuestMenuC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenQuestMenuC2SPacket(buf.readUUID());
    }

    public static void handle(OpenQuestMenuC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                QuestManager.openFromPacket(player, msg.villagerUuid);
            }
        });
        context.setPacketHandled(true);
    }
}
