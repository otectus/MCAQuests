package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.ClientProjectData;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client: the player's participating community projects, for the quest log + HUD (spec 0.4.0). */
public record ProjectLogSyncS2CPacket(List<ProjectLogEntry> entries) {

    public static void encode(ProjectLogSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.entries, ProjectLogEntry::encode);
    }

    public static ProjectLogSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectLogSyncS2CPacket(buf.readCollection(ArrayList::new, ProjectLogEntry::decode));
    }

    public static void handle(ProjectLogSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProjectData.updateProjects(msg.entries)));
        context.setPacketHandled(true);
    }
}
