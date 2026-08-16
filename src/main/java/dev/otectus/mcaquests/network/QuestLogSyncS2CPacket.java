package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.ClientQuestData;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client: the player's current active quests, for the quest log + HUD tracker (spec §20/§21). */
public record QuestLogSyncS2CPacket(List<QuestLogEntry> entries) {

    public static void encode(QuestLogSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.entries, QuestLogEntry::encode);
    }

    public static QuestLogSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestLogSyncS2CPacket(buf.readCollection(ArrayList::new, QuestLogEntry::decode));
    }

    public static void handle(QuestLogSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientQuestData.update(msg.entries)));
        context.setPacketHandled(true);
    }
}
