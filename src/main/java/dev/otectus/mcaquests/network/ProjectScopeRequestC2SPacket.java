package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: "show me where this project's work counts" (1.7.0). Names a project instance by
 * key; the server re-resolves it and answers only a player who is part of it or standing in it.
 */
public record ProjectScopeRequestC2SPacket(String instanceKey) implements CustomPacketPayload {

    public static final Type<ProjectScopeRequestC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_scope_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectScopeRequestC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectScopeRequestC2SPacket::encode, ProjectScopeRequestC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.instanceKey, 512);
    }

    public static ProjectScopeRequestC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ProjectScopeRequestC2SPacket(buf.readUtf(512));
    }

    public static void handle(ProjectScopeRequestC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !PacketRequests.allow(player)) {
            return;
        }
        ProjectManager.sendBuildArea(player, msg.instanceKey);
    }
}
