package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.JournalService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: request a fresh journal snapshot (sent when the journal screen opens) (spec 0.7.0). */
public record RequestJournalC2SPacket() {

    public static void encode(RequestJournalC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestJournalC2SPacket decode(FriendlyByteBuf buf) {
        return new RequestJournalC2SPacket();
    }

    public static void handle(RequestJournalC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                JournalService.sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }
}
