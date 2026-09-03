package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a community-project phase advanced — show a toast (spec 0.4.0). */
public record ProjectPhaseToastS2CPacket(Component projectTitle, Component phaseLabel)
        implements CustomPacketPayload {

    public static final Type<ProjectPhaseToastS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_phase_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectPhaseToastS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectPhaseToastS2CPacket::encode, ProjectPhaseToastS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        NetComponents.write(buf, this.projectTitle);
        NetComponents.write(buf, this.phaseLabel);
    }

    public static ProjectPhaseToastS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ProjectPhaseToastS2CPacket(NetComponents.read(buf), NetComponents.read(buf));
    }
}
