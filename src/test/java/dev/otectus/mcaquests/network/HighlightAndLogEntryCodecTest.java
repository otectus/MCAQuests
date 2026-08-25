package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestLogEntry;
import dev.otectus.mcaquests.support.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trips for the two pieces of the per-player highlight that cross the network, over a real
 * {@link FriendlyByteBuf} — no Minecraft server needed. Both are new in protocol 9.
 */
class HighlightAndLogEntryCodecTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Nested
    @DisplayName("HighlightTargetsS2CPacket")
    class Highlights {

        @Test
        @DisplayName("a set of entity ids round-trips exactly")
        void roundTrips() {
            HighlightTargetsS2CPacket original = new HighlightTargetsS2CPacket(new int[]{7, 42, 1337});

            FriendlyByteBuf buf = buffer();
            HighlightTargetsS2CPacket.encode(original, buf);
            HighlightTargetsS2CPacket decoded = HighlightTargetsS2CPacket.decode(buf);

            assertArrayEquals(original.entityIds(), decoded.entityIds());
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        }

        @Test
        @DisplayName("an empty set round-trips, because that is how a highlight is cleared")
        void emptyRoundTrips() {
            FriendlyByteBuf buf = buffer();
            HighlightTargetsS2CPacket.encode(new HighlightTargetsS2CPacket(new int[0]), buf);

            assertEquals(0, HighlightTargetsS2CPacket.decode(buf).entityIds().length,
                    "clearing the highlight sends an empty set, so it must survive the wire");
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        }

        @Test
        @DisplayName("large entity ids survive the VarInt encoding")
        void largeIds() {
            int[] ids = {Integer.MAX_VALUE, 0, 1};

            FriendlyByteBuf buf = buffer();
            HighlightTargetsS2CPacket.encode(new HighlightTargetsS2CPacket(ids), buf);

            assertArrayEquals(ids, HighlightTargetsS2CPacket.decode(buf).entityIds(),
                    "entity network ids are ordinary ints and can grow large in a long-lived world");
        }
    }

    @Nested
    @DisplayName("QuestLogEntry's new target hint")
    class LogEntry {

        private QuestLogEntry entryWith(Optional<QuestLogEntry.TargetHint> target) {
            return new QuestLogEntry(new ResourceLocation("mcaquests", "test_quest"),
                    UUID.randomUUID(), Component.literal("A Title"), Component.literal("Anna"),
                    Component.empty(), List.of(Component.literal("Deliver 1x Paper")), false, false,
                    OptionalLong.empty(), target);
        }

        private QuestLogEntry roundTrip(QuestLogEntry entry) {
            FriendlyByteBuf buf = buffer();
            QuestLogEntry.encode(buf, entry);
            QuestLogEntry decoded = QuestLogEntry.decode(buf);
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
            return decoded;
        }

        @Test
        @DisplayName("an entry with no target round-trips as empty")
        void absentTarget() {
            assertTrue(roundTrip(entryWith(Optional.empty())).target().isEmpty(),
                    "most quests point at nobody in particular; the HUD must render as it always did");
        }

        @Test
        @DisplayName("a target hint round-trips name, position and the last-known flag")
        void presentTarget() {
            QuestLogEntry.TargetHint hint =
                    new QuestLogEntry.TargetHint(Component.literal("Hans"), new BlockPos(120, 68, -340), true);

            QuestLogEntry.TargetHint decoded = roundTrip(entryWith(Optional.of(hint))).target().orElseThrow();

            assertEquals(hint.name().getString(), decoded.name().getString());
            assertEquals(hint.pos(), decoded.pos());
            assertTrue(decoded.lastKnown(),
                    "the HUD words the line differently for a last-known position, so the flag must survive");
        }

        @Test
        @DisplayName("negative coordinates survive intact")
        void negativeCoordinates() {
            // The reason the position is written as a packed BlockPos long rather than three VarInts:
            // VarInt is not zig-zag encoded, so every negative coordinate would silently cost 5 bytes.
            BlockPos pos = new BlockPos(-4096, -48, -30_000_000);
            QuestLogEntry.TargetHint hint = new QuestLogEntry.TargetHint(Component.literal("Greta"), pos, false);

            assertEquals(pos, roundTrip(entryWith(Optional.of(hint))).target().orElseThrow().pos(),
                    "a target in the far negative quadrant must still point the player the right way");
        }
    }
}
