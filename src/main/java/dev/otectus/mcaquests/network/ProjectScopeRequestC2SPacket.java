package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: "show me where this project's work counts" (1.7.0). Names a project instance by
 * key; the server re-resolves it and answers only a player who is part of it or standing in it.
 */
public record ProjectScopeRequestC2SPacket(String instanceKey) {

    public static void encode(ProjectScopeRequestC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.instanceKey, 512);
    }

    public static ProjectScopeRequestC2SPacket decode(FriendlyByteBuf buf) {
        return new ProjectScopeRequestC2SPacket(buf.readUtf(512));
    }

    public static void handle(ProjectScopeRequestC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        PacketRequests.enqueue(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ProjectManager.sendBuildArea(player, msg.instanceKey);
            }
        });
        context.setPacketHandled(true);
    }
}
