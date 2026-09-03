package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
public record HighlightTargetsS2CPacket(int[] entityIds) implements CustomPacketPayload {

    public static final Type<HighlightTargetsS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "highlight_targets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HighlightTargetsS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(HighlightTargetsS2CPacket::encode, HighlightTargetsS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarIntArray(this.entityIds);
    }

    public static HighlightTargetsS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new HighlightTargetsS2CPacket(buf.readVarIntArray());
    }
}
