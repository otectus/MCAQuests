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
 * Client to server: abandon an active quest from the quest log screen.
 *
 * <p>Distinct from {@link QuestAbandonC2SPacket}, which abandons from inside a villager's menu and so
 * re-sends that menu afterwards and requires the giver to be interactable. This one carries the giver's
 * UUID only to identify which active quest is meant (the same quest can be active from two villagers) —
 * the giver itself need not exist, which is what lets a player drop a quest whose villager is dead,
 * unloaded, or in another dimension. Carries identifiers only; the server re-resolves and re-validates
 * everything (never trust the client — spec section 20/26).
 */
public record QuestAbandonFromLogC2SPacket(UUID villagerUuid, ResourceLocation questId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestAbandonFromLogC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_abandon_from_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestAbandonFromLogC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestAbandonFromLogC2SPacket::encode, QuestAbandonFromLogC2SPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(QuestAbandonFromLogC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
    }

    public static QuestAbandonFromLogC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestAbandonFromLogC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(QuestAbandonFromLogC2SPacket msg, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuestManager.abandonFromLog(player, msg.villagerUuid, msg.questId);
        }
    }
}
