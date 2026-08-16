package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: a community-project phase advanced — show a toast (spec 0.4.0). */
public record ProjectPhaseToastS2CPacket(Component projectTitle, Component phaseLabel) {

    public static void encode(ProjectPhaseToastS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.projectTitle);
        buf.writeComponent(msg.phaseLabel);
    }

    public static ProjectPhaseToastS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectPhaseToastS2CPacket(buf.readComponent(), buf.readComponent());
    }

    public static void handle(ProjectPhaseToastS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.showProjectToast(msg.projectTitle, msg.phaseLabel)));
        context.setPacketHandled(true);
    }
}
