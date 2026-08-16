package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: contribute to a project at a sponsoring villager (spec 0.4.0). Fully validated and
 * re-resolved server-side; the server consumes the player's items for every contribution objective of
 * the current phase.
 */
public record ProjectContributeC2SPacket(UUID villagerUuid, ResourceLocation projectId) {

    public static void encode(ProjectContributeC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.projectId);
    }

    public static ProjectContributeC2SPacket decode(FriendlyByteBuf buf) {
        return new ProjectContributeC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(ProjectContributeC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ProjectManager.contributeFromPacket(player, msg.villagerUuid, msg.projectId);
            }
        });
        context.setPacketHandled(true);
    }
}
