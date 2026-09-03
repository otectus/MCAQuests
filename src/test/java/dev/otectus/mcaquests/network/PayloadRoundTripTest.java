package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceSnapshot;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §14.5: every one of the 21 payloads goes out over a real {@link RegistryFriendlyByteBuf} and
 * comes back carrying exactly what it left with, through the very {@code STREAM_CODEC} that
 * {@code QuestNetwork} registers rather than through the {@code encode}/{@code decode} pair directly.
 *
 * <p>The port rewrote all 21 by hand — {@code SimpleChannel}'s {@code registerMessage} triple became a
 * {@code CustomPacketPayload} with its own codec — and the failure mode of a hand-rewritten packet is
 * never a compile error. It is a field encoded and not decoded, which leaves the buffer misaligned so
 * that the <em>next</em> value read comes back as nonsense. Every case here therefore asserts the
 * buffer was drained exactly, which is what turns "one field is missing" into a failure at the packet
 * that dropped it.
 *
 * <p>Each payload is exercised with every component non-default (non-empty styled {@code Component}s,
 * non-zero numbers, present {@code Optional}s, populated collections, and — where the payload carries
 * items — a stack with a {@link DataComponents#CUSTOM_NAME} data component, which is the 1.21 shape a
 * {@code writeItem}-era port silently drops). Collection-bearing payloads get the empty case too,
 * since an empty list and a missing list are one byte apart on the wire.
 */
class PayloadRoundTripTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** The size the "maximum collection" cases use; see {@link #maximumCollections} for why it is 64. */
    private static final int MAX_ELEMENTS = 64;

    private static final UUID VILLAGER = UUID.fromString("d81ba1b0-0000-4000-8000-00000000f00d");
    private static final UUID OTHER_VILLAGER = UUID.fromString("d81ba1b0-0000-4000-8000-00000000beef");
    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "well_repair");
    private static final ResourceLocation PROJECT = ResourceLocation.fromNamespaceAndPath("mcaquests", "guardhouse");
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    // ---------------------------------------------------------------- fixtures

    /** A component nobody could confuse with {@code Component.empty()}: it carries style and a sibling. */
    private static Component styled(String text) {
        return Component.literal(text)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(true))
                .append(Component.translatable("mcaquests.test.suffix"));
    }

    /** A stack whose identity lives in a data component rather than in its item id. */
    private static ItemStack namedStack() {
        ItemStack stack = new ItemStack(Items.EMERALD, 7);
        stack.set(DataComponents.CUSTOM_NAME, styled("A Mason Fee"));
        return stack;
    }

    private static CardObjective objective() {
        return new CardObjective(styled("Deliver Wheat"), 3, 24, CardObjective.State.LOST, namedStack());
    }

    private static QuestCard questCard() {
        return new QuestCard(QUEST, styled("Repair the Well"), styled("The Well Chain"), styled("Bring me stone."),
                List.of(objective()), List.of(styled("3x Emerald")), List.of(namedStack()), "medium");
    }

    private static QuestLogEntry logEntry() {
        return new QuestLogEntry(QUEST, VILLAGER, styled("Repair the Well"), styled("Anna"),
                styled("The Well Chain"), List.of(objective()), true, true, true,
                OptionalLong.of(4242L), List.of(styled("The mill is idle")));
    }

    private static ProjectObjectiveLine projectLine() {
        return new ProjectObjectiveLine(styled("Stone delivered"), 40, 64, 12);
    }

    private static ProjectCard projectCard() {
        return new ProjectCard(PROJECT, styled("Guardhouse"), styled("Village"), styled("Sponsored by Otto"),
                styled("Phase 2"), styled("We need stone."), List.of(projectLine()), List.of(styled("Renown")),
                ProjectMenuStatus.IN_PROGRESS);
    }

    private static ProjectLogEntry projectLogEntry() {
        return new ProjectLogEntry(PROJECT, styled("Guardhouse"), styled("Otto"), styled("Village"),
                styled("Phase 2"), List.of(projectLine()));
    }

    private static JournalVillageEntry villageEntry() {
        return new JournalVillageEntry(OVERWORLD, 12, styled("Riverbend"), 240, styled("Friend"),
                styled("Champion"), 500, List.of(styled("Well-Digger")));
    }

    private static GuidanceTarget guidanceTarget() {
        return new GuidanceTarget(GuidanceKind.WORKSTATION, OptionalInt.of(9182), new BlockPos(-1400, 63, 2200),
                Level.NETHER, styled("A workstation"), 5, true, true, 1.95F);
    }

    private static GuidanceSnapshot snapshot() {
        return new GuidanceSnapshot(List.of(new ActiveGuidance(QUEST, VILLAGER, guidanceTarget()),
                new ActiveGuidance(PROJECT, OTHER_VILLAGER, guidanceTarget())), 1);
    }

    /** {@code n} distinct non-empty strings, so a truncated list shows up as a wrong element. */
    private static List<String> strings(int n, String prefix) {
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(prefix + i);
        }
        return List.copyOf(out);
    }

    private static <T> List<T> repeat(int n, T value) {
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(value);
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- machinery

    /**
     * Encodes through the payload's registered stream codec and decodes it back, insisting the buffer
     * is drained. The codec rather than {@code encode}/{@code decode} directly: that is the object
     * {@code PayloadRegistrar} is handed, so a codec wired to the wrong pair of methods fails here.
     */
    private static <T extends CustomPacketPayload> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                              T payload) {
        RegistryFriendlyByteBuf buf = TestRegistries.buffer();
        codec.encode(buf, payload);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        return decoded;
    }

    private static void assertStack(ItemStack expected, ItemStack actual, String what) {
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual),
                what + ": item and data components must survive (was " + actual + ")");
        assertEquals(expected.getCount(), actual.getCount(), what + ": count");
    }

    private static void assertObjective(CardObjective expected, CardObjective actual, String what) {
        assertEquals(expected.text(), actual.text(), what + ": text");
        assertEquals(expected.current(), actual.current(), what + ": current");
        assertEquals(expected.required(), actual.required(), what + ": required");
        assertEquals(expected.state(), actual.state(), what + ": state");
        assertStack(expected.icon(), actual.icon(), what + ": icon");
    }

    private static void assertQuestCard(QuestCard expected, QuestCard actual, String what) {
        assertEquals(expected.questId(), actual.questId(), what + ": questId");
        assertEquals(expected.title(), actual.title(), what + ": title");
        assertEquals(expected.chainLabel(), actual.chainLabel(), what + ": chainLabel");
        assertEquals(expected.dialogue(), actual.dialogue(), what + ": dialogue");
        assertEquals(expected.rewards(), actual.rewards(), what + ": rewards");
        assertEquals(expected.difficulty(), actual.difficulty(), what + ": difficulty");
        assertEquals(expected.objectives().size(), actual.objectives().size(), what + ": objective count");
        for (int i = 0; i < expected.objectives().size(); i++) {
            assertObjective(expected.objectives().get(i), actual.objectives().get(i), what + ": objective " + i);
        }
        assertEquals(expected.rewardIcons().size(), actual.rewardIcons().size(), what + ": reward icon count");
        for (int i = 0; i < expected.rewardIcons().size(); i++) {
            assertStack(expected.rewardIcons().get(i), actual.rewardIcons().get(i), what + ": reward icon " + i);
        }
    }

    private static void assertLogEntry(QuestLogEntry expected, QuestLogEntry actual, String what) {
        assertEquals(expected.questId(), actual.questId(), what + ": questId");
        assertEquals(expected.villagerUuid(), actual.villagerUuid(), what + ": villagerUuid");
        assertEquals(expected.title(), actual.title(), what + ": title");
        assertEquals(expected.giverName(), actual.giverName(), what + ": giverName");
        assertEquals(expected.chainLabel(), actual.chainLabel(), what + ": chainLabel");
        assertEquals(expected.ready(), actual.ready(), what + ": ready");
        assertEquals(expected.suspended(), actual.suspended(), what + ": suspended");
        assertEquals(expected.tracked(), actual.tracked(), what + ": tracked");
        assertEquals(expected.deadlineGameTime(), actual.deadlineGameTime(), what + ": deadline");
        assertEquals(expected.townsteadContext(), actual.townsteadContext(), what + ": townsteadContext");
        assertEquals(expected.objectives().size(), actual.objectives().size(), what + ": objective count");
        for (int i = 0; i < expected.objectives().size(); i++) {
            assertObjective(expected.objectives().get(i), actual.objectives().get(i), what + ": objective " + i);
        }
    }

    // ---------------------------------------------------------------- the 21 payloads, fully populated

    @Test
    @DisplayName("OpenQuestMenuC2SPacket round-trips")
    void openQuestMenu() {
        OpenQuestMenuC2SPacket packet = new OpenQuestMenuC2SPacket(VILLAGER);
        assertEquals(packet, roundTrip(OpenQuestMenuC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("QuestDecisionC2SPacket round-trips, accept and decline alike")
    void questDecision() {
        QuestDecisionC2SPacket accepted = new QuestDecisionC2SPacket(VILLAGER, QUEST, true);
        assertEquals(accepted, roundTrip(QuestDecisionC2SPacket.STREAM_CODEC, accepted));

        // The decline is the other half of one byte, and losing it would accept quests silently.
        QuestDecisionC2SPacket declined = new QuestDecisionC2SPacket(VILLAGER, QUEST, false);
        assertEquals(declined, roundTrip(QuestDecisionC2SPacket.STREAM_CODEC, declined));
        assertNotEquals(accepted, declined);
    }

    @Test
    @DisplayName("QuestTurnInC2SPacket round-trips")
    void questTurnIn() {
        QuestTurnInC2SPacket packet = new QuestTurnInC2SPacket(VILLAGER, QUEST);
        assertEquals(packet, roundTrip(QuestTurnInC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("QuestAbandonC2SPacket round-trips")
    void questAbandon() {
        QuestAbandonC2SPacket packet = new QuestAbandonC2SPacket(VILLAGER, QUEST);
        assertEquals(packet, roundTrip(QuestAbandonC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("QuestMenuDataS2CPacket round-trips the header and every card")
    void questMenuData() {
        QuestMenuDataS2CPacket packet = new QuestMenuDataS2CPacket(VILLAGER, styled("Anna"), styled("Farmer"),
                7, styled("A word with you?"), QuestMenuStatus.READY, List.of(questCard(), questCard()));

        QuestMenuDataS2CPacket decoded = roundTrip(QuestMenuDataS2CPacket.STREAM_CODEC, packet);

        assertEquals(packet.villagerUuid(), decoded.villagerUuid());
        assertEquals(packet.villagerName(), decoded.villagerName());
        assertEquals(packet.profession(), decoded.profession());
        assertEquals(packet.hearts(), decoded.hearts());
        assertEquals(packet.greeting(), decoded.greeting());
        assertEquals(packet.status(), decoded.status());
        assertEquals(2, decoded.cards().size());
        assertQuestCard(packet.cards().get(0), decoded.cards().get(0), "card 0");
        assertQuestCard(packet.cards().get(1), decoded.cards().get(1), "card 1");
    }

    @Test
    @DisplayName("QuestLogSyncS2CPacket round-trips every entry field")
    void questLogSync() {
        QuestLogSyncS2CPacket packet = new QuestLogSyncS2CPacket(List.of(logEntry()));

        QuestLogSyncS2CPacket decoded = roundTrip(QuestLogSyncS2CPacket.STREAM_CODEC, packet);

        assertEquals(1, decoded.entries().size());
        assertLogEntry(packet.entries().get(0), decoded.entries().get(0), "entry 0");
    }

    @Test
    @DisplayName("QuestReadyToastS2CPacket round-trips")
    void questReadyToast() {
        QuestReadyToastS2CPacket packet = new QuestReadyToastS2CPacket(styled("Repair the Well"));
        assertEquals(packet, roundTrip(QuestReadyToastS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("ProjectContributeC2SPacket round-trips")
    void projectContribute() {
        ProjectContributeC2SPacket packet = new ProjectContributeC2SPacket(VILLAGER, PROJECT);
        assertEquals(packet, roundTrip(ProjectContributeC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("ProjectMenuDataS2CPacket round-trips")
    void projectMenuData() {
        ProjectMenuDataS2CPacket packet = new ProjectMenuDataS2CPacket(VILLAGER, List.of(projectCard()));
        assertEquals(packet, roundTrip(ProjectMenuDataS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("ProjectLogSyncS2CPacket round-trips")
    void projectLogSync() {
        ProjectLogSyncS2CPacket packet = new ProjectLogSyncS2CPacket(List.of(projectLogEntry()));
        assertEquals(packet, roundTrip(ProjectLogSyncS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("ProjectPhaseToastS2CPacket round-trips both of its components")
    void projectPhaseToast() {
        // Two components in a row is exactly where a codec that writes two and reads one goes wrong.
        ProjectPhaseToastS2CPacket packet =
                new ProjectPhaseToastS2CPacket(styled("Guardhouse"), styled("Phase 2"));
        assertEquals(packet, roundTrip(ProjectPhaseToastS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("ReputationTierToastS2CPacket round-trips")
    void reputationTierToast() {
        ReputationTierToastS2CPacket packet = new ReputationTierToastS2CPacket(styled("Champion"));
        assertEquals(packet, roundTrip(ReputationTierToastS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("RequestJournalC2SPacket writes nothing at all and still round-trips")
    void requestJournal() {
        RegistryFriendlyByteBuf buf = TestRegistries.buffer();
        RequestJournalC2SPacket.STREAM_CODEC.encode(buf, new RequestJournalC2SPacket());
        assertEquals(0, buf.readableBytes(), "a fieldless payload must write nothing at all");
        assertEquals(new RequestJournalC2SPacket(), RequestJournalC2SPacket.STREAM_CODEC.decode(buf));
    }

    @Test
    @DisplayName("JournalSyncS2CPacket round-trips all three lists and the Reputation flag")
    void journalSync() {
        JournalSyncS2CPacket packet = new JournalSyncS2CPacket(List.of(styled("Hero")),
                List.of(villageEntry()), List.of(new JournalArchiveEntry(styled("Repair the Well"), 4)), true);
        assertEquals(packet, roundTrip(JournalSyncS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("SituationToastS2CPacket round-trips")
    void situationToast() {
        SituationToastS2CPacket packet = new SituationToastS2CPacket(styled("Riverbend needs help"));
        assertEquals(packet, roundTrip(SituationToastS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("FtbqEditorIdsS2CPacket round-trips all seven lists, distinctly")
    void ftbqEditorIds() {
        // Seven same-typed lists in a row: a codec that reads them in the wrong order still drains the
        // buffer, so only distinct contents can catch it.
        FtbqEditorIdsS2CPacket packet = new FtbqEditorIdsS2CPacket(strings(2, "quest"), strings(2, "chain"),
                strings(2, "ladder"), strings(2, "tier"), strings(2, "title"), strings(2, "project"),
                strings(2, "situation"));
        assertEquals(packet, roundTrip(FtbqEditorIdsS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("QuestAbandonFromLogC2SPacket round-trips")
    void questAbandonFromLog() {
        QuestAbandonFromLogC2SPacket packet = new QuestAbandonFromLogC2SPacket(VILLAGER, QUEST);
        assertEquals(packet, roundTrip(QuestAbandonFromLogC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("OpenStandingC2SPacket round-trips")
    void openStanding() {
        OpenStandingC2SPacket packet = new OpenStandingC2SPacket(OVERWORLD, 12);
        assertEquals(packet, roundTrip(OpenStandingC2SPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("HighlightTargetsS2CPacket round-trips the complete id array")
    void highlightTargets() {
        int[] ids = {1, 4096, 77, 1_000_000};
        HighlightTargetsS2CPacket decoded =
                roundTrip(HighlightTargetsS2CPacket.STREAM_CODEC, new HighlightTargetsS2CPacket(ids));
        assertArrayEquals(ids, decoded.entityIds());
    }

    @Test
    @DisplayName("QuestGuidanceS2CPacket round-trips the snapshot, its targets and the primary index")
    void questGuidance() {
        QuestGuidanceS2CPacket packet = new QuestGuidanceS2CPacket(snapshot());
        QuestGuidanceS2CPacket decoded = roundTrip(QuestGuidanceS2CPacket.STREAM_CODEC, packet);
        assertEquals(packet.snapshot(), decoded.snapshot());
        assertEquals(1, decoded.snapshot().primary(), "the marker must stay on the entry it was on");
    }

    @Test
    @DisplayName("QuestTrackC2SPacket round-trips both present and both absent")
    void questTrack() {
        QuestTrackC2SPacket following = QuestTrackC2SPacket.of(VILLAGER, QUEST);
        assertEquals(following, roundTrip(QuestTrackC2SPacket.STREAM_CODEC, following));

        // "Stop following" is a real message: an empty payload that must not decode as a present
        // Optional, or the pin would never come off.
        QuestTrackC2SPacket none = QuestTrackC2SPacket.none();
        QuestTrackC2SPacket decoded = roundTrip(QuestTrackC2SPacket.STREAM_CODEC, none);
        assertEquals(Optional.empty(), decoded.villagerUuid());
        assertEquals(Optional.empty(), decoded.questId());
    }

    // ---------------------------------------------------------------- empty collections

    @Test
    @DisplayName("every collection-bearing payload round-trips with all of its collections empty")
    void emptyCollections() {
        // An empty list and a list that was never written differ by a single zero byte, and the
        // difference only shows up in whatever is read after it.
        QuestMenuDataS2CPacket menu = new QuestMenuDataS2CPacket(VILLAGER, Component.empty(), Component.empty(),
                0, Component.empty(), QuestMenuStatus.NO_QUESTS, List.of());
        assertTrue(roundTrip(QuestMenuDataS2CPacket.STREAM_CODEC, menu).cards().isEmpty());

        assertTrue(roundTrip(QuestLogSyncS2CPacket.STREAM_CODEC, new QuestLogSyncS2CPacket(List.of()))
                .entries().isEmpty());
        assertTrue(roundTrip(ProjectLogSyncS2CPacket.STREAM_CODEC, new ProjectLogSyncS2CPacket(List.of()))
                .entries().isEmpty());
        assertTrue(roundTrip(ProjectMenuDataS2CPacket.STREAM_CODEC,
                new ProjectMenuDataS2CPacket(VILLAGER, List.of())).cards().isEmpty());

        JournalSyncS2CPacket journal = new JournalSyncS2CPacket(List.of(), List.of(), List.of(), false);
        assertEquals(journal, roundTrip(JournalSyncS2CPacket.STREAM_CODEC, journal));

        FtbqEditorIdsS2CPacket ids = new FtbqEditorIdsS2CPacket(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        assertEquals(ids, roundTrip(FtbqEditorIdsS2CPacket.STREAM_CODEC, ids));

        assertArrayEquals(new int[0], roundTrip(HighlightTargetsS2CPacket.STREAM_CODEC,
                new HighlightTargetsS2CPacket(new int[0])).entityIds());

        // The empty snapshot is what takes a marker away, so it must not arrive looking like entry 0.
        QuestGuidanceS2CPacket guidance = new QuestGuidanceS2CPacket(GuidanceSnapshot.EMPTY);
        QuestGuidanceS2CPacket decodedGuidance = roundTrip(QuestGuidanceS2CPacket.STREAM_CODEC, guidance);
        assertTrue(decodedGuidance.snapshot().isEmpty());
        assertEquals(-1, decodedGuidance.snapshot().primary());

        // A card with no objectives, rewards or icons: three empty collections in a row.
        QuestCard bare = new QuestCard(QUEST, Component.empty(), Component.empty(), Component.empty(),
                List.of(), List.of(), List.of(), "");
        QuestMenuDataS2CPacket bareMenu = new QuestMenuDataS2CPacket(VILLAGER, Component.empty(),
                Component.empty(), 0, Component.empty(), QuestMenuStatus.OFFER, List.of(bare));
        assertQuestCard(bare, roundTrip(QuestMenuDataS2CPacket.STREAM_CODEC, bareMenu).cards().get(0), "bare card");
    }

    // ---------------------------------------------------------------- maximum collections

    @Test
    @DisplayName("every collection-bearing payload round-trips at the maximum size the suite exercises")
    void maximumCollections() {
        // 1.5.3 declares no element-count bound on any of these DTOs (see
        // oversizedCollectionsAreBoundedOnlyByTheBuffer), so there is no constant to read and 64 stands
        // in for one: comfortably past any list the server actually builds -- offers per villager,
        // active quests, journal villages -- and past the point where a VarInt length prefix stops
        // being a single byte, which is the boundary a hand-written prefix is likeliest to get wrong.
        QuestMenuDataS2CPacket menu = new QuestMenuDataS2CPacket(VILLAGER, styled("Anna"), styled("Farmer"),
                10, styled("Hello"), QuestMenuStatus.OFFER, repeat(MAX_ELEMENTS, questCard()));
        assertEquals(MAX_ELEMENTS, roundTrip(QuestMenuDataS2CPacket.STREAM_CODEC, menu).cards().size());

        assertEquals(MAX_ELEMENTS, roundTrip(QuestLogSyncS2CPacket.STREAM_CODEC,
                new QuestLogSyncS2CPacket(repeat(MAX_ELEMENTS, logEntry()))).entries().size());
        assertEquals(MAX_ELEMENTS, roundTrip(ProjectLogSyncS2CPacket.STREAM_CODEC,
                new ProjectLogSyncS2CPacket(repeat(MAX_ELEMENTS, projectLogEntry()))).entries().size());
        assertEquals(MAX_ELEMENTS, roundTrip(ProjectMenuDataS2CPacket.STREAM_CODEC,
                new ProjectMenuDataS2CPacket(VILLAGER, repeat(MAX_ELEMENTS, projectCard()))).cards().size());

        JournalSyncS2CPacket journal = new JournalSyncS2CPacket(repeat(MAX_ELEMENTS, styled("Hero")),
                repeat(MAX_ELEMENTS, villageEntry()),
                repeat(MAX_ELEMENTS, new JournalArchiveEntry(styled("Repair the Well"), 4)), true);
        assertEquals(journal, roundTrip(JournalSyncS2CPacket.STREAM_CODEC, journal));

        FtbqEditorIdsS2CPacket ids = new FtbqEditorIdsS2CPacket(strings(MAX_ELEMENTS, "quest"),
                strings(MAX_ELEMENTS, "chain"), strings(MAX_ELEMENTS, "ladder"), strings(MAX_ELEMENTS, "tier"),
                strings(MAX_ELEMENTS, "title"), strings(MAX_ELEMENTS, "project"),
                strings(MAX_ELEMENTS, "situation"));
        assertEquals(ids, roundTrip(FtbqEditorIdsS2CPacket.STREAM_CODEC, ids));

        int[] entityIds = new int[MAX_ELEMENTS];
        for (int i = 0; i < entityIds.length; i++) {
            entityIds[i] = i * 1000 + 1;
        }
        assertArrayEquals(entityIds, roundTrip(HighlightTargetsS2CPacket.STREAM_CODEC,
                new HighlightTargetsS2CPacket(entityIds)).entityIds());

        List<ActiveGuidance> guidances =
                repeat(MAX_ELEMENTS, new ActiveGuidance(QUEST, VILLAGER, guidanceTarget()));
        QuestGuidanceS2CPacket guidance =
                new QuestGuidanceS2CPacket(new GuidanceSnapshot(guidances, MAX_ELEMENTS - 1));
        QuestGuidanceS2CPacket decoded = roundTrip(QuestGuidanceS2CPacket.STREAM_CODEC, guidance);
        assertEquals(MAX_ELEMENTS, decoded.snapshot().all().size());
        assertEquals(MAX_ELEMENTS - 1, decoded.snapshot().primary());
    }

    // ---------------------------------------------------------------- malformed and oversized input

    /**
     * The FTB id sync is the one payload with a declared bound, and it is a <em>byte</em> budget applied
     * when the packet is built rather than a count checked on decode — so this asserts the guard where
     * it actually lives.
     */
    @Test
    @DisplayName("FtbqEditorIdsS2CPacket.build truncates past its byte budget, and what survives round-trips")
    void ftbqByteBudgetTruncates() {
        String entry = "mcaquests:" + "x".repeat(90);
        int perEntry = entry.length() + 2;
        int overBudget = FtbqEditorIdsS2CPacket.BYTE_BUDGET / perEntry + 50;

        FtbqEditorIdsS2CPacket packet = FtbqEditorIdsS2CPacket.build(repeat(overBudget, entry), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertTrue(packet.questIds().size() < overBudget, "the budget must have dropped something");
        assertEquals(packet, roundTrip(FtbqEditorIdsS2CPacket.STREAM_CODEC, packet));
    }

    @Test
    @DisplayName("a collection whose declared size outruns the payload is rejected on decode")
    void oversizedCollectionsAreBoundedOnlyByTheBuffer() {
        // PORT NOTE: no mcaquests DTO declares a maximum element count, and 1.21's
        // FriendlyByteBuf.readCollection bounds only the ArrayList's *initial capacity*
        // (ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE), never the count it then loops over. So the thing
        // rejecting an oversized collection is the buffer running out of elements to read -- which it
        // does. A forged count with no data behind it fails loudly rather than being believed.
        RegistryFriendlyByteBuf logBuf = TestRegistries.buffer();
        logBuf.writeVarInt(Short.MAX_VALUE); // "here come 32767 log entries"
        assertThrows(RuntimeException.class, () -> QuestLogSyncS2CPacket.STREAM_CODEC.decode(logBuf),
                "a declared entry count with no entries behind it must not decode");

        RegistryFriendlyByteBuf journalBuf = TestRegistries.buffer();
        journalBuf.writeVarInt(Short.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> JournalSyncS2CPacket.STREAM_CODEC.decode(journalBuf));

        RegistryFriendlyByteBuf idsBuf = TestRegistries.buffer();
        idsBuf.writeVarInt(Short.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> FtbqEditorIdsS2CPacket.STREAM_CODEC.decode(idsBuf));
    }

    @Test
    @DisplayName("HighlightTargetsS2CPacket is bounded by the buffer's own readVarIntArray guard")
    void oversizedEntityIdArrayIsRejected() {
        // The one collection the vanilla API bounds for us: readVarIntArray() caps the count at the
        // buffer's readable byte count, so a forged length is a DecoderException rather than an
        // int[Integer.MAX_VALUE] allocation.
        RegistryFriendlyByteBuf buf = TestRegistries.buffer();
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> HighlightTargetsS2CPacket.STREAM_CODEC.decode(buf));
    }

    @Test
    @DisplayName("an out-of-range enum ordinal is rejected rather than read as some other state")
    void malformedEnumOrdinalIsRejected() {
        // QuestMenuStatus travels through writeEnum/readEnum, and 1.21's readEnum indexes
        // getEnumConstants() with the transmitted ordinal directly -- so a forged ordinal throws here
        // instead of arriving as a valid-looking status. (CardObjective.State and GuidanceKind
        // deliberately do not: they decode through byOrdinal, which falls back to a default so that
        // appending a state stays backward-compatible. Those two are the exception; this is the rule.)
        RegistryFriendlyByteBuf buf = TestRegistries.buffer();
        buf.writeUUID(VILLAGER);
        NetComponents.write(buf, styled("Anna"));
        NetComponents.write(buf, styled("Farmer"));
        buf.writeVarInt(7);
        NetComponents.write(buf, styled("Hello"));
        buf.writeVarInt(QuestMenuStatus.values().length); // one past the last valid ordinal
        buf.writeVarInt(0); // no cards

        assertThrows(RuntimeException.class, () -> QuestMenuDataS2CPacket.STREAM_CODEC.decode(buf));
    }
}
