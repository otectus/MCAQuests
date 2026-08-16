package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: a quest became ready to turn in — show a toast + sound (spec sections 20, 21). */
public record QuestReadyToastS2CPacket(Component title) {

    public static void encode(QuestReadyToastS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.title);
    }

    public static QuestReadyToastS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestReadyToastS2CPacket(buf.readComponent());
    }

    public static void handle(QuestReadyToastS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.showReadyToast(msg.title)));
        context.setPacketHandled(true);
    }
}
