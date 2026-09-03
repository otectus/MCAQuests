package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
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

    public static final Type<ProjectMenuDataS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_menu_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectMenuDataS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ProjectMenuDataS2CPacket::encode, ProjectMenuDataS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        buf.writeCollection(this.cards, (b, v) -> ProjectCard.encode((RegistryFriendlyByteBuf) b, v));
    }

    public static ProjectMenuDataS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ProjectMenuDataS2CPacket(buf.readUUID(),
                buf.readCollection(ArrayList::new, b -> ProjectCard.decode((RegistryFriendlyByteBuf) b)));
    }
}
