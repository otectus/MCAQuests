package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.delivery.DeliveryRequest;
import dev.otectus.mcaquests.support.TestBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delivery packet's wire contract: what survives a round trip, and what a forged one cannot do.
 *
 * <p>This is the only packet in the mod that leads to items leaving a player's inventory, so the
 * decoder is written to bound everything it reads <em>before</em> any of it reaches the server thread.
 * The properties asserted here are the ones a hostile client would otherwise get for free: an
 * objective index far outside any definition, a quantity larger than any inventory, a slot list longer
 * than a player has slots, and a negative revision that would read as "do not check".
 *
 * <p>None of this is the real defence — {@code DeliveryService} re-derives every fact against server
 * state and takes only units it removed itself — but a packet that can allocate or loop on a
 * client-supplied number has already lost something before that validation runs.
 *
 * @see PacketBoundsTest the same discipline for the collection decoding all packets share
 */
class DeliverPacketBoundsTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final ResourceLocation QUEST = new ResourceLocation("mcaquests", "last_banner_home");

    private static QuestDeliverC2SPacket roundTrip(QuestDeliverC2SPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            QuestDeliverC2SPacket.encode(packet, buf);
            QuestDeliverC2SPacket decoded = QuestDeliverC2SPacket.decode(buf);
            assertEquals(0, buf.readableBytes(), "the decoder must consume exactly what the encoder wrote");
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("a plain Deliver click survives the wire unchanged")
    void plainClickRoundTrips() {
        QuestDeliverC2SPacket packet = QuestDeliverC2SPacket.all(VILLAGER, Optional.of(INSTANCE), QUEST,
                2, 1, false);
        QuestDeliverC2SPacket decoded = roundTrip(packet);

        assertEquals(VILLAGER, decoded.villagerUuid());
        assertEquals(Optional.of(INSTANCE), decoded.instance());
        assertEquals(QUEST, decoded.questId());
        assertEquals(2, decoded.objectiveIndex());
        assertEquals(DeliveryRequest.ALL_UNITS, decoded.units(), "\"everything outstanding\" must survive");
        assertEquals(1, decoded.expectedRevision());
        assertEquals(packet.requestId(), decoded.requestId());
        assertTrue(decoded.slots().isEmpty(), "no explicit selection means the default slot policy");
    }

    @Test
    @DisplayName("an explicit selection keeps its slots, quantity and Deliver-and-complete flag")
    void explicitSelectionRoundTrips() {
        IntSet slots = new IntOpenHashSet(new int[] {0, 7, 30});
        QuestDeliverC2SPacket decoded = roundTrip(new QuestDeliverC2SPacket(VILLAGER, Optional.empty(),
                QUEST, 0, 2, slots, DeliveryRequest.NO_REVISION, REQUEST, true));

        assertEquals(slots, decoded.slots());
        assertEquals(2, decoded.units());
        assertEquals(DeliveryRequest.NO_REVISION, decoded.expectedRevision());
        assertTrue(decoded.thenComplete());
        assertEquals(Optional.empty(), decoded.instance(),
                "a pre-1.6.5 copy has no instance id, and the packet must be able to say so");
    }

    @Test
    @DisplayName("the request it becomes carries the id and the revision the click was made at")
    void requestCarriesIdentityAndRevision() {
        DeliveryRequest request = new QuestDeliverC2SPacket(VILLAGER, Optional.of(INSTANCE), QUEST, 1, 3,
                IntSets.EMPTY_SET, 4, REQUEST, false).request();

        assertEquals(REQUEST, request.requestId(), "a replayed packet has to be recognisable");
        assertEquals(4, request.expectedRevision());
        assertTrue(request.hasRevision());
        assertEquals(DeliveryRequest.Method.MENU, request.method());
        assertEquals(INSTANCE, request.instance());
    }

    @Test
    @DisplayName("an impossible objective index and quantity are clamped, never looped on")
    void forgedCountsAreClamped() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(VILLAGER);
            buf.writeOptional(Optional.<UUID>empty(), FriendlyByteBuf::writeUUID);
            buf.writeResourceLocation(QUEST);
            buf.writeVarInt(Integer.MAX_VALUE);       // objective index
            buf.writeVarInt(Integer.MAX_VALUE);       // units + 1
            buf.writeVarInt(0);                       // no slots
            buf.writeVarInt(0);                       // revision + 1 -> NO_REVISION
            buf.writeUUID(REQUEST);
            buf.writeBoolean(false);

            QuestDeliverC2SPacket decoded = QuestDeliverC2SPacket.decode(buf);
            assertTrue(decoded.objectiveIndex() <= 256, "objective index: " + decoded.objectiveIndex());
            assertEquals(DeliveryRequest.MAX_UNITS, decoded.units());
            assertEquals(DeliveryRequest.NO_REVISION, decoded.expectedRevision());
            assertEquals(DeliveryRequest.MAX_UNITS, decoded.request().requestedUnits(),
                    "the request the server acts on carries the clamped figure, not the claimed one");
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("a slot list longer than a player inventory is rejected outright")
    void forgedSlotListIsRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(VILLAGER);
            buf.writeOptional(Optional.<UUID>empty(), FriendlyByteBuf::writeUUID);
            buf.writeResourceLocation(QUEST);
            buf.writeVarInt(0);
            buf.writeVarInt(1);
            buf.writeVarInt(64); // sixty-four source slots for a forty-one slot inventory
            for (int i = 0; i < 64; i++) {
                buf.writeVarInt(i);
            }
            buf.writeVarInt(0);
            buf.writeUUID(REQUEST);
            buf.writeBoolean(false);

            assertThrows(DecoderException.class, () -> QuestDeliverC2SPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("a wildly long claimed slot list is refused before any element is read")
    void impossibleSlotLengthIsRefusedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(VILLAGER);
            buf.writeOptional(Optional.<UUID>empty(), FriendlyByteBuf::writeUUID);
            buf.writeResourceLocation(QUEST);
            buf.writeVarInt(0);
            buf.writeVarInt(1);
            buf.writeVarInt(Integer.MAX_VALUE); // claimed slot count, with no bytes behind it

            assertThrows(DecoderException.class, () -> QuestDeliverC2SPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("out-of-range slot indices are dropped rather than carried into the transaction")
    void slotsOutsideAnInventoryAreDropped() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(VILLAGER);
            buf.writeOptional(Optional.<UUID>empty(), FriendlyByteBuf::writeUUID);
            buf.writeResourceLocation(QUEST);
            buf.writeVarInt(0);
            buf.writeVarInt(2);
            buf.writeCollection(java.util.List.of(3, 999, 40), FriendlyByteBuf::writeVarInt);
            buf.writeVarInt(1);
            buf.writeUUID(REQUEST);
            buf.writeBoolean(false);

            QuestDeliverC2SPacket decoded = QuestDeliverC2SPacket.decode(buf);
            assertEquals(new IntOpenHashSet(new int[] {3, 40}), decoded.slots(),
                    "a slot no inventory has cannot authorize anything");
        } finally {
            buf.release();
        }
    }
}
