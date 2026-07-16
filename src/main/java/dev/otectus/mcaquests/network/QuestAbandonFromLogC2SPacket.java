package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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
public record QuestAbandonFromLogC2SPacket(UUID villagerUuid, ResourceLocation questId) {

    public static void encode(QuestAbandonFromLogC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeResourceLocation(msg.questId);
    }

    public static QuestAbandonFromLogC2SPacket decode(FriendlyByteBuf buf) {
        return new QuestAbandonFromLogC2SPacket(buf.readUUID(), buf.readResourceLocation());
    }

    public static void handle(QuestAbandonFromLogC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                QuestManager.abandonFromLog(player, msg.villagerUuid, msg.questId);
            }
        });
        context.setPacketHandled(true);
    }
}
