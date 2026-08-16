package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.ClientJournalData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client: a full snapshot of the player's progression journal (spec 0.7.0). */
public record JournalSyncS2CPacket(List<Component> globalTitles, List<JournalVillageEntry> villages,
                                   List<JournalArchiveEntry> archive) {

    public static void encode(JournalSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.globalTitles, FriendlyByteBuf::writeComponent);
        buf.writeCollection(msg.villages, JournalVillageEntry::encode);
        buf.writeCollection(msg.archive, JournalArchiveEntry::encode);
    }

    public static JournalSyncS2CPacket decode(FriendlyByteBuf buf) {
        List<Component> globalTitles = buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent);
        List<JournalVillageEntry> villages = buf.readCollection(ArrayList::new, JournalVillageEntry::decode);
        List<JournalArchiveEntry> archive = buf.readCollection(ArrayList::new, JournalArchiveEntry::decode);
        return new JournalSyncS2CPacket(globalTitles, villages, archive);
    }

    public static void handle(JournalSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientJournalData.update(msg)));
        context.setPacketHandled(true);
    }
}
