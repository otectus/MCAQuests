package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: a full snapshot of the player's progression journal (spec 0.7.0). */
public record JournalSyncS2CPacket(List<Component> globalTitles, List<JournalVillageEntry> villages,
                                   List<JournalArchiveEntry> archive) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JournalSyncS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "journal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalSyncS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(JournalSyncS2CPacket::encode, JournalSyncS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(JournalSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.globalTitles, NetComponents::write);
        buf.writeCollection(msg.villages, JournalVillageEntry::encode);
        buf.writeCollection(msg.archive, JournalArchiveEntry::encode);
    }

    public static JournalSyncS2CPacket decode(FriendlyByteBuf buf) {
        List<Component> globalTitles = buf.readCollection(ArrayList::new, NetComponents::read);
        List<JournalVillageEntry> villages = buf.readCollection(ArrayList::new, JournalVillageEntry::decode);
        List<JournalArchiveEntry> archive = buf.readCollection(ArrayList::new, JournalArchiveEntry::decode);
        return new JournalSyncS2CPacket(globalTitles, villages, archive);
    }
}
