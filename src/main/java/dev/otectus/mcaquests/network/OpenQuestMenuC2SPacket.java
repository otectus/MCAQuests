package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client to server: "I clicked the Quests button on this villager." Carries only the villager UUID;
 * the server re-resolves and re-validates everything (never trust the client — spec section 20/26).
 */
public record OpenQuestMenuC2SPacket(UUID villagerUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenQuestMenuC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "open_quest_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestMenuC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenQuestMenuC2SPacket::encode, OpenQuestMenuC2SPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenQuestMenuC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
    }

    public static OpenQuestMenuC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenQuestMenuC2SPacket(buf.readUUID());
    }

    public static void handle(OpenQuestMenuC2SPacket msg, IPayloadContext context) {
        // PORT: NeoForge payload handlers run on the main thread by default — same semantics as the
        // old ctx.enqueueWork(...) wrapper.
        if (context.player() instanceof ServerPlayer player) {
            QuestManager.openFromPacket(player, msg.villagerUuid);
        }
    }
}
