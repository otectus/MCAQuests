package dev.otectus.mcaquests.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketBoundsTest {
    @Test
    void validLargeCollectionsAreNotLimitedByInitialAllocationSize() {
        List<Integer> entries = java.util.Collections.nCopies(10_000, 1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeCollection(entries, FriendlyByteBuf::writeVarInt);
            assertEquals(entries, PacketCollections.readList(buf, FriendlyByteBuf::readVarInt));
        } finally {
            buf.release();
        }
    }
    @Test
    void impossibleLengthsAreRejectedBeforeAllocatingOrReadingElements() {
        for (int count : new int[]{-1, Integer.MAX_VALUE, 2}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(count);
                buf.writeByte(1);
                assertThrows(DecoderException.class, () -> PacketCollections.readList(buf, b -> {
                    fail("Impossible collection must be rejected before decoding elements");
                    return 0;
                }));
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void validEmptyAndSingleByteEntriesKeepTheirWireFormat() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeCollection(List.of(1, 2, 3), FriendlyByteBuf::writeVarInt);
            buf.writeVarInt(0);
            assertEquals(List.of(1, 2, 3), PacketCollections.readList(buf, FriendlyByteBuf::readVarInt));
            assertEquals(List.of(), PacketCollections.readList(buf, FriendlyByteBuf::readVarInt));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void actualEditorPacketRejectsForgedHugeList() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class, () -> FtbqEditorIdsS2CPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void requestFloodIsBoundedAndRecoversWithoutDelayingNormalClicks() {
        PacketRequests.Budget budget = new PacketRequests.Budget();
        for (int i = 0; i < 20; i++) {
            assertTrue(budget.take(1000));
        }
        for (int i = 0; i < 1000; i++) {
            assertFalse(budget.take(1000));
        }
        assertFalse(budget.take(1099));
        assertTrue(budget.take(1100));
        assertFalse(budget.take(1100));
        assertTrue(budget.take(1200));
    }

    @Test
    void requestBudgetsAreIndependentAndNeverBankMoreThanOneBurst() {
        PacketRequests.Budget first = new PacketRequests.Budget();
        PacketRequests.Budget second = new PacketRequests.Budget();
        for (int i = 0; i < 20; i++) {
            assertTrue(first.take(0));
        }
        assertFalse(first.take(0));
        assertTrue(second.take(0));
        for (int i = 0; i < 20; i++) {
            assertTrue(first.take(1_000_000));
        }
        assertFalse(first.take(1_000_000));
    }
}
