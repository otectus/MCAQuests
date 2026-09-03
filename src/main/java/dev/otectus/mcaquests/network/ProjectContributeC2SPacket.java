package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client to server: contribute to a project at a sponsoring villager (spec 0.4.0). Fully validated and
 * re-resolved server-side; the server consumes the player's items for every contribution objective of
 * the current phase.
 */
public record ProjectContributeC2SPacket(UUID villagerUuid, ResourceLocation projectId)
        implements CustomPacketPayload {

    public static final Type<ProjectContributeC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_contribute"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectContributeC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectContributeC2SPacket::encode, ProjectContributeC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        buf.writeResourceLocation(this.projectId);
    }

    public static ProjectContributeC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ProjectContributeC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(ProjectContributeC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ProjectManager.contributeFromPacket(player, msg.villagerUuid, msg.projectId);
    }
}
