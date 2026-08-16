package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.JournalService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: request a fresh journal snapshot (sent when the journal screen opens) (spec 0.7.0). */
public record RequestJournalC2SPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestJournalC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "request_journal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestJournalC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(RequestJournalC2SPacket::encode, RequestJournalC2SPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RequestJournalC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestJournalC2SPacket decode(FriendlyByteBuf buf) {
        return new RequestJournalC2SPacket();
    }

    public static void handle(RequestJournalC2SPacket msg, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            JournalService.sendSnapshot(player);
        }
    }
}
