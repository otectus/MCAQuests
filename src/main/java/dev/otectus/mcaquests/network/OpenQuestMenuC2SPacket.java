package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestManager;
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

    public static final Type<OpenQuestMenuC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "open_quest_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestMenuC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenQuestMenuC2SPacket::encode, OpenQuestMenuC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
    }

    public static OpenQuestMenuC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenQuestMenuC2SPacket(buf.readUUID());
    }

    public static void handle(OpenQuestMenuC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        QuestManager.openFromPacket(player, msg.villagerUuid);
    }
}
