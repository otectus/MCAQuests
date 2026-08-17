package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: the journal's View Deeds link (§29.7) — "open the standing screen for this
 * village". The client supplies only an address; the server checks that MCA: Reputation is canonical
 * and that this player actually knows the named village before pushing anything, so a forged packet
 * can at worst open the player's own standing somewhere they legitimately stand.
 */
public record OpenStandingC2SPacket(ResourceLocation dimension, int villageId) {

    public static void encode(OpenStandingC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeVarInt(msg.villageId);
    }

    public static OpenStandingC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenStandingC2SPacket(buf.readResourceLocation(), buf.readVarInt());
    }

    public static void handle(OpenStandingC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ReputationBridge.isCanonical()) {
                return;
            }
            if (!knowsVillage(player, msg.dimension, msg.villageId)) {
                McaQuests.LOGGER.debug("[MCA: Quests] {} asked to view deeds for {}|{} without any "
                                + "standing there; ignoring", player.getGameProfile().getName(),
                        msg.dimension, msg.villageId);
                return;
            }
            ReputationBridge.backend().openStandingScreen(player, msg.dimension, msg.villageId);
        });
        context.setPacketHandled(true);
    }

    /** The same membership rule the journal itself lists villages by: standing or a held title. */
    private static boolean knowsVillage(ServerPlayer player, ResourceLocation dimension, int villageId) {
        if (ReputationBridge.backend()
                .villageScores(player.server, player.getUUID(), dimension).containsKey(villageId)) {
            return true;
        }
        return QuestCapabilities.get(player)
                .map(data -> !data.titles().forVillage(dimension, villageId).isEmpty())
                .orElse(false);
    }
}
