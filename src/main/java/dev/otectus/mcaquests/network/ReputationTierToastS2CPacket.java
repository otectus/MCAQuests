package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the player reached a new reputation tier with a village — show a toast (spec 0.7.0). */
public record ReputationTierToastS2CPacket(Component tierName) {

    public static void encode(ReputationTierToastS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.tierName);
    }

    public static ReputationTierToastS2CPacket decode(FriendlyByteBuf buf) {
        return new ReputationTierToastS2CPacket(buf.readComponent());
    }

    public static void handle(ReputationTierToastS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.showReputationTierToast(msg.tierName)));
        context.setPacketHandled(true);
    }
}
