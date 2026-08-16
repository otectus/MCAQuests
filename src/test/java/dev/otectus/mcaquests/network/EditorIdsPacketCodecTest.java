package dev.otectus.mcaquests.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry-free round-trip + truncation tests for {@link FtbqEditorIdsS2CPacket} (spec §29.1 #9, task
 * M5.1). Uses a real {@link FriendlyByteBuf} over a Netty {@link Unpooled#buffer()} — no Minecraft
 * server or FTB jar required, matching the "no MC server / no FTB on the test classpath" constraint.
 */
class EditorIdsPacketCodecTest {

    @Test
    void roundTripsAllSevenListsThroughARealBuffer() {
        FtbqEditorIdsS2CPacket original = FtbqEditorIdsS2CPacket.build(
                List.of("mcaquests:quest_a", "mcaquests:quest_b"),
                List.of("the_family_farm|The Family Farm", "no_name_chain|no_name_chain"),
                List.of("mcaquests:default", "mcaquests:custom"),
                List.of("mcaquests:default|stranger", "mcaquests:default|friend", "mcaquests:custom|elder"),
                List.of("mcaquests:wandering_helper"),
                List.of("mcaquests:rebuild_the_mill"),
                List.of("mcaquests:bandit_raid"));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FtbqEditorIdsS2CPacket.encode(original, buf);
        FtbqEditorIdsS2CPacket decoded = FtbqEditorIdsS2CPacket.decode(buf);

        assertEquals(original.questIds(), decoded.questIds());
        assertEquals(original.chainEntries(), decoded.chainEntries());
        assertEquals(original.ladderIds(), decoded.ladderIds());
        assertEquals(original.ladderTierEntries(), decoded.ladderTierEntries());
        assertEquals(original.titleIds(), decoded.titleIds());
        assertEquals(original.projectIds(), decoded.projectIds());
        assertEquals(original.situationIds(), decoded.situationIds());
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
    }

    @Test
    void emptyListsRoundTripCleanly() {
        FtbqEditorIdsS2CPacket empty = FtbqEditorIdsS2CPacket.build(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FtbqEditorIdsS2CPacket.encode(empty, buf);
        FtbqEditorIdsS2CPacket decoded = FtbqEditorIdsS2CPacket.decode(buf);

        assertTrue(decoded.questIds().isEmpty());
        assertTrue(decoded.chainEntries().isEmpty());
        assertTrue(decoded.ladderIds().isEmpty());
        assertTrue(decoded.ladderTierEntries().isEmpty());
        assertTrue(decoded.titleIds().isEmpty());
        assertTrue(decoded.projectIds().isEmpty());
        assertTrue(decoded.situationIds().isEmpty());
    }

    @Test
    void buildTruncatesOversizedInputToTheByteBudget() {
        // Each identical entry costs exactly 1024 bytes on the wire (1022 UTF-8 bytes + the 2-byte
        // length-prefix allowance), and BYTE_BUDGET (65536) is an exact multiple of that: 64 entries
        // consume the whole budget with zero bytes left over, so nothing afterwards — not even
        // questIds' own 65th entry, nor titleIds' small entry — can fit. This pins down the exact
        // truncation count instead of just "some truncation happened".
        String entry = "x".repeat(1022);
        List<String> oversizedQuestIds = new ArrayList<>(Collections.nCopies(100, entry));
        List<String> someTitleIds = List.of("mcaquests:should_be_dropped");

        FtbqEditorIdsS2CPacket packet = FtbqEditorIdsS2CPacket.build(
                oversizedQuestIds, List.of(), List.of(), List.of(), someTitleIds, List.of(), List.of());

        assertEquals(64, packet.questIds().size(), "exactly 64 entries (1024 bytes each) fill the 64 KB budget");
        assertEquals(oversizedQuestIds.subList(0, 64), packet.questIds(),
                "kept entries must be a prefix in original (§20) order, nothing reordered");
        assertTrue(packet.titleIds().isEmpty(),
                "budget exhausted (zero bytes left over) by questIds must also drop every later list");
    }

    @Test
    void buildDoesNotTruncateWhenUnderBudget() {
        FtbqEditorIdsS2CPacket packet = FtbqEditorIdsS2CPacket.build(
                List.of("mcaquests:a"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(1, packet.questIds().size());
    }
}
