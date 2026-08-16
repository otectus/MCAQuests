package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the player's current active quests, for the quest log + HUD tracker (spec §20/§21). */
public record QuestLogSyncS2CPacket(List<QuestLogEntry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestLogSyncS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_log_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestLogSyncS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestLogSyncS2CPacket::encode, QuestLogSyncS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(QuestLogSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.entries, QuestLogEntry::encode);
    }

    public static QuestLogSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestLogSyncS2CPacket(buf.readCollection(ArrayList::new, QuestLogEntry::decode));
    }
}
