package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: a new situation opened in a nearby village — show the "needs help" toast (0.8.0). */
public record SituationToastS2CPacket(Component title) {

    public static void encode(SituationToastS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.title);
    }

    public static SituationToastS2CPacket decode(FriendlyByteBuf buf) {
        return new SituationToastS2CPacket(buf.readComponent());
    }

    public static void handle(SituationToastS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.showSituationToast(msg.title)));
        context.setPacketHandled(true);
    }
}
