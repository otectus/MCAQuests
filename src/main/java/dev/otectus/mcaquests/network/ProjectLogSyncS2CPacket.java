package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the player's participating community projects, for the quest log + HUD (spec 0.4.0). */
public record ProjectLogSyncS2CPacket(List<ProjectLogEntry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProjectLogSyncS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_log_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectLogSyncS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectLogSyncS2CPacket::encode, ProjectLogSyncS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ProjectLogSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.entries, ProjectLogEntry::encode);
    }

    public static ProjectLogSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectLogSyncS2CPacket(buf.readCollection(ArrayList::new, ProjectLogEntry::decode));
    }
}
