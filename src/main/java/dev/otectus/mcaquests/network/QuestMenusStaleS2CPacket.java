package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the quest catalogue changed under an open menu (a {@code /reload}), so a client
 * showing a villager's Quests menu asks for a fresh one (1.7.0). Carries nothing; a client with no quest
 * menu open ignores it, so nothing opens that the player had closed.
 */
public record QuestMenusStaleS2CPacket() {

    public static void encode(QuestMenusStaleS2CPacket msg, FriendlyByteBuf buf) {
    }

    public static QuestMenusStaleS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestMenusStaleS2CPacket();
    }

    public static void handle(QuestMenusStaleS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> QuestClientHandlers::refreshOpenQuestMenu));
        context.setPacketHandled(true);
    }
}
