package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a community-project phase advanced — show a toast (spec 0.4.0). */
public record ProjectPhaseToastS2CPacket(Component projectTitle, Component phaseLabel)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProjectPhaseToastS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_phase_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectPhaseToastS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectPhaseToastS2CPacket::encode, ProjectPhaseToastS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ProjectPhaseToastS2CPacket msg, FriendlyByteBuf buf) {
        NetComponents.write(buf, msg.projectTitle);
        NetComponents.write(buf, msg.phaseLabel);
    }

    public static ProjectPhaseToastS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectPhaseToastS2CPacket(NetComponents.read(buf), NetComponents.read(buf));
    }
}
