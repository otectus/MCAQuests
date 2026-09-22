package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: where a project's positional work counts, as the geometry the server itself tests
 * (1.6.6). {@code summary} is the line the client prints — project, village, dimension, allowance and
 * whether the outline is exact — and {@code materials} what counts there.
 */
public record ProjectScopeS2CPacket(Component title, Component summary, ScopeGeometry geometry,
                                    List<Component> materials) implements CustomPacketPayload {

    public static final Type<ProjectScopeS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_scope"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectScopeS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectScopeS2CPacket::encode, ProjectScopeS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        NetComponents.write(buf, this.title);
        NetComponents.write(buf, this.summary);
        this.geometry.encode(buf);
        buf.writeCollection(this.materials, NetComponents::write);
    }

    public static ProjectScopeS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ProjectScopeS2CPacket(NetComponents.read(buf), NetComponents.read(buf), ScopeGeometry.decode(buf),
                PacketCollections.readList(buf, NetComponents::read));
    }
}
