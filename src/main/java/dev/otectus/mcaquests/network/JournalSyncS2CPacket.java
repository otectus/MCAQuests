package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a full snapshot of the player's progression journal (spec 0.7.0).
 * {@code reputationPresent} says whether MCA: Reputation is canonical on this server, which is what
 * decides whether the journal offers the View Deeds link (§29.7).
 */
public record JournalSyncS2CPacket(List<Component> globalTitles, List<JournalVillageEntry> villages,
                                   List<JournalArchiveEntry> archive, boolean reputationPresent)
        implements CustomPacketPayload {

    public static final Type<JournalSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "journal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalSyncS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(JournalSyncS2CPacket::encode, JournalSyncS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(this.globalTitles, NetComponents::write);
        buf.writeCollection(this.villages, (b, v) -> JournalVillageEntry.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeCollection(this.archive, (b, v) -> JournalArchiveEntry.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeBoolean(this.reputationPresent);
    }

    public static JournalSyncS2CPacket decode(RegistryFriendlyByteBuf buf) {
        List<Component> globalTitles = buf.readCollection(ArrayList::new, NetComponents::read);
        List<JournalVillageEntry> villages = buf.readCollection(ArrayList::new,
                b -> JournalVillageEntry.decode((RegistryFriendlyByteBuf) b));
        List<JournalArchiveEntry> archive = buf.readCollection(ArrayList::new,
                b -> JournalArchiveEntry.decode((RegistryFriendlyByteBuf) b));
        boolean reputationPresent = buf.readBoolean();
        return new JournalSyncS2CPacket(globalTitles, villages, archive, reputationPresent);
    }
}
