package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: the community projects a villager is sponsoring/offering (spec 0.4.0). Cached
 * (not auto-opened) so the villager menu can show a "View Project" button. Sent alongside the quest
 * menu packet.
 */
public record ProjectMenuDataS2CPacket(UUID villagerUuid, List<ProjectCard> cards) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProjectMenuDataS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_menu_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectMenuDataS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectMenuDataS2CPacket::encode, ProjectMenuDataS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ProjectMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeCollection(msg.cards, ProjectCard::encode);
    }

    public static ProjectMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        return new ProjectMenuDataS2CPacket(buf.readUUID(),
                buf.readCollection(ArrayList::new, ProjectCard::decode));
    }
}
