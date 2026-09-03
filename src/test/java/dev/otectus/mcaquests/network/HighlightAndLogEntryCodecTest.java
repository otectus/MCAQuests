package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.support.TestRegistries;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trips for the two pieces of the per-player highlight that cross the network, over a real
 * {@link RegistryFriendlyByteBuf} — no Minecraft server needed. Both are new in protocol 9.
 */
class HighlightAndLogEntryCodecTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return TestRegistries.buffer();
    }

    @Nested
    @DisplayName("HighlightTargetsS2CPacket")
    class Highlights {

        @Test
        @DisplayName("a set of entity ids round-trips exactly")
        void roundTrips() {
            HighlightTargetsS2CPacket original = new HighlightTargetsS2CPacket(new int[]{7, 42, 1337});

            RegistryFriendlyByteBuf buf = buffer();
            original.encode(buf);
            HighlightTargetsS2CPacket decoded = HighlightTargetsS2CPacket.decode(buf);

            assertArrayEquals(original.entityIds(), decoded.entityIds());
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        }

        @Test
        @DisplayName("an empty set round-trips, because that is how a highlight is cleared")
        void emptyRoundTrips() {
            RegistryFriendlyByteBuf buf = buffer();
            new HighlightTargetsS2CPacket(new int[0]).encode(buf);

            assertEquals(0, HighlightTargetsS2CPacket.decode(buf).entityIds().length,
                    "clearing the highlight sends an empty set, so it must survive the wire");
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        }

        @Test
        @DisplayName("large entity ids survive the VarInt encoding")
        void largeIds() {
            int[] ids = {Integer.MAX_VALUE, 0, 1};

            RegistryFriendlyByteBuf buf = buffer();
            new HighlightTargetsS2CPacket(ids).encode(buf);

            assertArrayEquals(ids, HighlightTargetsS2CPacket.decode(buf).entityIds(),
                    "entity network ids are ordinary ints and can grow large in a long-lived world");
        }
    }

    @Nested
    @DisplayName("QuestLogEntry after the target hint was removed")
    class LogEntry {

        /**
         * The entry no longer carries a destination at all.
         *
         * <p>It used to, as a {@code TargetHint} of a name and a {@code BlockPos} with no dimension,
         * which could only ever name a villager — a quest about an ancient city had nothing to put in
         * it. {@code GuidanceTarget} answers the same question for places as well as people, with the
         * dimension attached, and since protocol 13 the guidance packet carries one per quest. Two
         * answers to one question is how they drift apart, so there is now one.
         */
        private QuestLogEntry entry(List<CardObjective> objectives, boolean tracked) {
            return new QuestLogEntry(ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest"),
                    UUID.randomUUID(), Component.literal("A Title"), Component.literal("Anna"),
                    Component.empty(), objectives, false, false, tracked, OptionalLong.empty(),
                    List.of());
        }

        private QuestLogEntry roundTrip(QuestLogEntry entry) {
            RegistryFriendlyByteBuf buf = buffer();
            QuestLogEntry.encode(buf, entry);
            QuestLogEntry decoded = QuestLogEntry.decode(buf);
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
            return decoded;
        }

        @Test
        @DisplayName("an entry round-trips its objectives and its follow pin")
        void entryRoundTrips() {
            QuestLogEntry entry = entry(List.of(CardObjective.offered(
                    Component.literal("Deliver 1x Paper"), 1, new ItemStack(Items.PAPER))), true);

            QuestLogEntry decoded = roundTrip(entry);

            assertEquals(entry.questId(), decoded.questId());
            assertEquals(entry.villagerUuid(), decoded.villagerUuid());
            assertEquals(1, decoded.objectives().size());
            assertTrue(decoded.tracked(), "the log draws the pin on this row, so the flag must survive");
        }

        @Test
        @DisplayName("an entry with no objectives round-trips")
        void emptyObjectives() {
            // The shape QuestManager sends for a quest whose definition vanished on a datapack reload:
            // still listed, under its raw id, so the player can abandon it.
            assertTrue(roundTrip(entry(List.of(), false)).objectives().isEmpty());
        }
    }
}
