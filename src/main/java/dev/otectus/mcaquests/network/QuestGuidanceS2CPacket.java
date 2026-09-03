package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.guidance.GuidanceSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: every place this player's quests are sending them, and which one carries the
 * marker.
 *
 * <p>Sent to a single player, like {@link HighlightTargetsS2CPacket} and for the same reason — where
 * you are going is yours, and nobody else on the server should see a beacon standing on your quest.
 *
 * <p>It carried exactly one target until 1.5.0, on the argument that a client handed several would
 * have had to decide between them itself. That was the right argument about the <em>marker</em> and
 * the wrong one about the tracker, which lists every active quest and could only say where one of
 * them was going. The decision still belongs to the server, so it is still made there and travels as
 * {@code GuidanceSnapshot.primary} — an index into the list rather than a second copy of the target,
 * so the marker and the row it belongs to can never disagree.
 *
 * <p>Each entry names its quest, because the marker is not always about the quest the player pinned —
 * a pinned quest with nothing to point at falls through to one that can — and the tracker has to draw
 * each destination under the row it belongs to.
 *
 * <p>An empty snapshot is a real message and not an absence of one: it is how a marker is taken away
 * when an objective completes, a quest ends, or the feature is turned off. The server only sends when
 * the answer changes, so the steady state costs nothing.
 */
public record QuestGuidanceS2CPacket(GuidanceSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<QuestGuidanceS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_guidance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestGuidanceS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestGuidanceS2CPacket::encode, QuestGuidanceS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        GuidanceSnapshot.encode(buf, this.snapshot);
    }

    public static QuestGuidanceS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestGuidanceS2CPacket(GuidanceSnapshot.decode(buf));
    }
}
