package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: where a project's positional work counts, as the geometry the server itself tests
 * (1.7.0). {@code summary} is the line the client prints — project, village, dimension, allowance and
 * whether the outline is exact — and {@code materials} what counts there.
 */
public record ProjectScopeS2CPacket(Component title, Component summary, ScopeGeometry geometry,
                                    List<Component> materials) {

    public static void encode(ProjectScopeS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.title);
        buf.writeComponent(msg.summary);
        msg.geometry.encode(buf);
        buf.writeCollection(msg.materials, FriendlyByteBuf::writeComponent);
    }

    public static ProjectScopeS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectScopeS2CPacket(buf.readComponent(), buf.readComponent(), ScopeGeometry.decode(buf),
                PacketCollections.readList(buf, FriendlyByteBuf::readComponent));
    }

    public static void handle(ProjectScopeS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                QuestClientHandlers.onBuildArea(msg.title, msg.summary, msg.geometry, msg.materials)));
        context.setPacketHandled(true);
    }
}
