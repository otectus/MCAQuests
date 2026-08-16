package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: the community projects a villager is sponsoring/offering (spec 0.4.0). Cached
 * (not auto-opened) so the villager menu can show a "View Project" button. Sent alongside the quest
 * menu packet.
 */
public record ProjectMenuDataS2CPacket(UUID villagerUuid, List<ProjectCard> cards) {

    public static void encode(ProjectMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeCollection(msg.cards, ProjectCard::encode);
    }

    public static ProjectMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectMenuDataS2CPacket(buf.readUUID(),
                buf.readCollection(ArrayList::new, ProjectCard::decode));
    }

    public static void handle(ProjectMenuDataS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.onProjectMenuData(msg.villagerUuid, msg.cards)));
        context.setPacketHandled(true);
    }
}
