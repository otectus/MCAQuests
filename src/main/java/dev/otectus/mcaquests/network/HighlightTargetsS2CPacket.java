package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the complete set of entity ids this player's active quests want highlighted.
 *
 * <p>Sent to <b>one player</b>, which is the point — the highlight used to be a vanilla Glowing effect
 * applied to the villager itself, so every player on the server saw everyone else's quest markers. This
 * carries the ids to the quest owner only and the client draws the outline locally.
 *
 * <p>Always the full set rather than a delta: it is a handful of ints, the server only sends it when the
 * set actually changes, and a full set means a dropped or reordered update can never leave a villager
 * glowing forever.
 */
public record HighlightTargetsS2CPacket(int[] entityIds) {

    public static void encode(HighlightTargetsS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.entityIds);
    }

    public static HighlightTargetsS2CPacket decode(FriendlyByteBuf buf) {
        return new HighlightTargetsS2CPacket(buf.readVarIntArray());
    }

    public static void handle(HighlightTargetsS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.setHighlights(msg.entityIds)));
        context.setPacketHandled(true);
    }
}
