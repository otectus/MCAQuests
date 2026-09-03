package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceSnapshot;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trips for the two packets protocol 12 adds, over a real {@link FriendlyByteBuf}.
 *
 * <p>Every case drains the buffer, for the reason {@code QuestCardCodecTest} spells out: a packet that
 * encodes more than it decodes does not fail where the mistake was made, it leaves the buffer
 * misaligned and corrupts whatever is read next.
 *
 * <p>{@link GuidanceTarget} is the one that has to be exercised in both of its shapes. An entity-backed
 * target and a fixed position differ only by an {@code OptionalInt} written as a sentinel {@code -1},
 * and a sentinel that decodes as a real entity id would put the marker on whatever entity happened to
 * be numbered that — a bug that would look like the marker following the wrong villager.
 */
class GuidanceCodecTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static GuidanceTarget roundTrip(GuidanceTarget target) {
        FriendlyByteBuf buf = buffer();
        GuidanceTarget.encode(buf, target);
        GuidanceTarget decoded = GuidanceTarget.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        return decoded;
    }

    @Test
    @DisplayName("a fixed position round-trips, and carries no entity")
    void fixedPositionRoundTrips() {
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.HOME, OptionalInt.empty(),
                new BlockPos(-1400, 63, 2200), Level.OVERWORLD, Component.literal("Anna's home"),
                4, true, false, 0.0F);

        GuidanceTarget decoded = roundTrip(target);

        assertEquals(target, decoded);
        assertTrue(decoded.entityId().isEmpty(), "no entity must not decode as entity id -1");
        // Negative coordinates are why the position is a long rather than three VarInts: a VarInt is
        // unsigned-biased and each negative component would cost the full five bytes.
        assertEquals(-1400, decoded.pos().getX());
        assertTrue(decoded.approximate());
    }

    @Test
    @DisplayName("an entity-backed target keeps its id, including id 0")
    void entityTargetRoundTrips() {
        // Id 0 is the case a naive "0 means absent" encoding gets wrong, and entity ids do start there.
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.VILLAGER, OptionalInt.of(0),
                BlockPos.ZERO, Level.NETHER, Component.literal("Hans"), 3, false, false, 1.95F);

        GuidanceTarget decoded = roundTrip(target);

        assertEquals(target, decoded);
        assertEquals(0, decoded.entityId().orElse(-1));
        assertEquals(Level.NETHER, decoded.dimension());
        assertFalse(decoded.approximate());
    }

    @Test
    @DisplayName("an entity's height survives the wire, so an unloaded target still anchors on a body")
    void entityHeightRoundTrips() {
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.VILLAGER, OptionalInt.of(412),
                new BlockPos(60, 70, -12), Level.OVERWORLD, Component.literal("Marlene"), 3, false,
                true, 1.95F);

        assertEquals(1.95F, roundTrip(target).entityHeight(),
                "without the height the marker has nothing but a pair of feet to draw on");
    }

    @Test
    @DisplayName("a nonsensical height is clamped rather than trusted")
    void corruptHeightIsClamped() {
        // This one multiplies straight into the marker's anchor, so a hostile or simply broken sender
        // could put the glyph out of the world. The clamp is on decode for that reason.
        assertEquals(GuidanceTarget.MAX_ENTITY_HEIGHT, roundTrip(withHeight(1000.0F)).entityHeight());
        assertEquals(0.0F, roundTrip(withHeight(-1.0F)).entityHeight());
        assertEquals(0.0F, roundTrip(withHeight(Float.NaN)).entityHeight());
    }

    private static GuidanceTarget withHeight(float height) {
        return new GuidanceTarget(GuidanceKind.VILLAGER, OptionalInt.of(1), BlockPos.ZERO,
                Level.OVERWORLD, Component.literal("Hans"), 3, false, false, height);
    }

    @Test
    @DisplayName("a full snapshot of five destinations stays under a kilobyte")
    void fiveTargetSnapshotIsUnderOneKiB() {
        // Five quests is a busy log, and this packet is resent whenever guidance changes. Labels are
        // the expensive part -- a Component is JSON on the wire -- so they are as long as the quest
        // log will ever show.
        String name = "x".repeat(64);
        List<ActiveGuidance> all = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GuidanceTarget target = new GuidanceTarget(GuidanceKind.VILLAGER, OptionalInt.of(i),
                    new BlockPos(-2000 - i, 63, 3000 + i), Level.OVERWORLD, Component.literal(name),
                    3, false, false, 1.95F);
            all.add(new ActiveGuidance(new ResourceLocation("mcaquests", "quest_" + i),
                    UUID.randomUUID(), target));
        }

        FriendlyByteBuf buf = buffer();
        QuestGuidanceS2CPacket.encode(new QuestGuidanceS2CPacket(new GuidanceSnapshot(all, 0)), buf);

        assertTrue(buf.readableBytes() < 1024,
                "the guidance packet grew to " + buf.readableBytes() + " bytes for five destinations");
    }

    @Test
    @DisplayName("an unknown kind ordinal decodes as a place rather than throwing")
    void unknownKindIsTolerated() {
        // A client one build ahead of its server can send a kind this build has no name for. Drawing a
        // generic pin is right; refusing to decode the packet would drop the marker entirely.
        assertEquals(GuidanceKind.LOCATION, GuidanceKind.byOrdinal(9999));
        assertEquals(GuidanceKind.LOCATION, GuidanceKind.byOrdinal(-1));
    }

    @Test
    @DisplayName("the guidance packet round-trips a destination per quest, and which one is marked")
    void guidancePacketRoundTrips() {
        GuidanceTarget portal = new GuidanceTarget(GuidanceKind.PORTAL, OptionalInt.empty(),
                new BlockPos(12, 70, -8), Level.OVERWORLD, Component.literal("Way into the Nether"),
                8, false, false, 0.0F);
        GuidanceTarget fortress = new GuidanceTarget(GuidanceKind.STRUCTURE, OptionalInt.empty(),
                new BlockPos(1024, 68, -330), Level.OVERWORLD, Component.literal("Fortress"),
                24, true, false, 0.0F);
        ActiveGuidance relay = new ActiveGuidance(
                new ResourceLocation("mcaquests", "adventurer_nether_relay"), UUID.randomUUID(), portal);
        ActiveGuidance trial = new ActiveGuidance(
                new ResourceLocation("mcaquests", "adventurer_trial_by_fire"), UUID.randomUUID(), fortress);

        // Two quests, and the second one carries the marker: the pinned quest is not always the one
        // that can answer, which is the whole reason the index travels rather than being inferred.
        QuestGuidanceS2CPacket decoded =
                roundTrip(new QuestGuidanceS2CPacket(new GuidanceSnapshot(List.of(relay, trial), 1)));

        assertEquals(List.of(relay, trial), decoded.snapshot().all());
        assertEquals(Optional.of(trial), decoded.snapshot().primaryGuidance());
        // The quest travels with each destination because the tracker has to draw it under its own row.
        assertTrue(decoded.snapshot().all().get(0).isAbout(relay.questId(), relay.villagerUuid()));
        assertFalse(decoded.snapshot().all().get(0)
                .isAbout(relay.questId(), UUID.randomUUID()), "a different giver is a different quest");

        // Empty is a real message, not an absence of one: it is how a marker is taken away.
        QuestGuidanceS2CPacket empty = roundTrip(new QuestGuidanceS2CPacket(GuidanceSnapshot.EMPTY));
        assertTrue(empty.snapshot().all().isEmpty());
        assertEquals(Optional.empty(), empty.snapshot().primaryGuidance());
    }

    @Test
    @DisplayName("a snapshot of destinations with no marker among them is a legal payload")
    void snapshotWithoutAPrimary() {
        // Not a hypothetical: every quest here could be one whose objective is unreadable, in which
        // case the tracker still has lines to draw and nothing should carry a beam. An out-of-range
        // index is clamped rather than trusted, because it arrives over a network.
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.VILLAGE, OptionalInt.empty(),
                new BlockPos(-40, 64, 512), Level.OVERWORLD, Component.literal("Riverbend"),
                24, false, true, 0.0F);
        ActiveGuidance guidance = new ActiveGuidance(
                new ResourceLocation("mcaquests", "townstead_first_shift"), UUID.randomUUID(), target);

        GuidanceSnapshot decoded =
                roundTrip(new QuestGuidanceS2CPacket(new GuidanceSnapshot(List.of(guidance), 7))).snapshot();

        assertEquals(1, decoded.all().size());
        assertEquals(Optional.empty(), decoded.primaryGuidance(),
                "an index past the end must read as 'nothing marked', never as an exception");
        assertTrue(decoded.all().get(0).target().lastKnown(),
                "the tracker says 'last seen' rather than 'about' for this, so the flag must survive");
    }

    private static QuestGuidanceS2CPacket roundTrip(QuestGuidanceS2CPacket packet) {
        FriendlyByteBuf buf = buffer();
        QuestGuidanceS2CPacket.encode(packet, buf);
        QuestGuidanceS2CPacket decoded = QuestGuidanceS2CPacket.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        return decoded;
    }

    @Test
    @DisplayName("the track packet round-trips a quest and the request to follow nothing")
    void trackPacketRoundTrips() {
        UUID villager = UUID.randomUUID();
        ResourceLocation quest = new ResourceLocation("mcaquests", "relations_walk_me_to_bed");

        QuestTrackC2SPacket decoded = roundTrip(QuestTrackC2SPacket.of(villager, quest));
        assertEquals(Optional.of(villager), decoded.villagerUuid());
        assertEquals(Optional.of(quest), decoded.questId());

        QuestTrackC2SPacket none = roundTrip(QuestTrackC2SPacket.none());
        assertTrue(none.villagerUuid().isEmpty());
        assertTrue(none.questId().isEmpty());
    }

    private static QuestTrackC2SPacket roundTrip(QuestTrackC2SPacket packet) {
        FriendlyByteBuf buf = buffer();
        QuestTrackC2SPacket.encode(packet, buf);
        QuestTrackC2SPacket decoded = QuestTrackC2SPacket.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        return decoded;
    }
}
