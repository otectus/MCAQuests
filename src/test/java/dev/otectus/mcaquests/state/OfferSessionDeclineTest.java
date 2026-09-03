package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.quest.template.ResolvedValue;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The offer set a villager remembers, and what declining does to it.
 *
 * <p>The reported bug in one sentence: "when you hit decline it changes the context/story of every quest
 * of that villager, but the three quests remain the same, so it doesn't actually decline the quest." Both
 * halves come from the offer menu being recomputed on every open. These tests pin the two properties that
 * had nowhere to live before there was a session to hold them — <b>the refusal is remembered</b>, and
 * <b>the cards you did not touch do not move</b>.
 *
 * <p>Deliberately a test of the remembered set rather than of the whole draw. Drawing needs a
 * {@code ServerPlayer} and a {@code ServerLevel}, neither of which can be constructed in this environment;
 * everything that decides what a decline <em>means</em> was put on this class so it could be tested here.
 */
class OfferSessionDeclineTest {

    private static final UUID VILLAGER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ResourceLocation LETTER = ResourceLocation.fromNamespaceAndPath("mcaquests", "relations_letter_to_brother");
    private static final ResourceLocation MEND = ResourceLocation.fromNamespaceAndPath("mcaquests", "relations_mend_the_quarrel");
    private static final ResourceLocation TOY = ResourceLocation.fromNamespaceAndPath("mcaquests", "relations_childs_first_toy");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static OfferSession.Slot slot(ResourceLocation id) {
        return new OfferSession.Slot(id, null, Component.literal("A word about " + id.getPath() + "."));
    }

    /** A three-card set, drawn at game time 1000 under pack generation 1. */
    private static OfferSession session() {
        OfferSession session = new OfferSession(VILLAGER);
        session.redraw(List.of(slot(LETTER), slot(MEND), slot(TOY)), 1000L, 1, 4242L);
        return session;
    }

    @Test
    @DisplayName("declining removes exactly that card and leaves the others untouched")
    void decliningRemovesOnlyThatSlot() {
        OfferSession session = session();
        OfferSession.Slot first = session.slots().get(0);
        OfferSession.Slot third = session.slots().get(2);

        int index = session.removeSlot(MEND);

        assertEquals(1, index, "the middle card was declined, so the middle slot is the one to refill");
        assertEquals(2, session.slots().size());
        // Identity, not just equality: nothing was rebuilt, so the dialogue and the frozen template values
        // of the untouched cards are necessarily the same objects the player was already looking at.
        assertTrue(first == session.slots().get(0), "the card above the declined one moved");
        assertTrue(third == session.slots().get(1), "the card below the declined one was rebuilt");
    }

    @Test
    @DisplayName("declining a quest this villager never offered changes nothing")
    void decliningAnUnofferedQuestIsRejected() {
        OfferSession session = session();
        List<OfferSession.Slot> before = List.copyOf(session.slots());

        int index = session.removeSlot(ResourceLocation.fromNamespaceAndPath("somepack", "not_on_the_menu"));

        assertEquals(-1, index, "a client may only decline what it was actually offered");
        assertEquals(before, session.slots());
    }

    @Test
    @DisplayName("declining twice is idempotent")
    void decliningTwiceChangesNothingTheSecondTime() {
        OfferSession session = session();
        assertTrue(session.removeSlot(LETTER) >= 0);
        session.decline(LETTER, 1000L, 0);

        assertEquals(-1, session.removeSlot(LETTER));
        assertTrue(session.isDeclined(LETTER, 1000L));
    }

    @Test
    @DisplayName("a declined quest is not drawn back into a slot")
    void declinedQuestsAreSuppressed() {
        OfferSession session = session();
        session.decline(LETTER, 1000L, 0);

        assertTrue(session.isDeclined(LETTER, 1000L));
        assertFalse(session.isDeclined(MEND, 1000L), "declining one offer says nothing about the others");
    }

    @Test
    @DisplayName("with the default cooldown of zero, the refusal lasts until the set rerolls")
    void zeroCooldownLastsUntilTheNextDraw() {
        OfferSession session = session();
        session.decline(LETTER, 1000L, 0);

        // The obvious encoding — store now + 0 — would have made the refusal lapse on the tick it was
        // recorded, which is the original bug wearing a different hat.
        assertTrue(session.isDeclined(LETTER, 1000L));
        assertTrue(session.isDeclined(LETTER, 1_000_000L), "still refused, however long the set survives");

        session.redraw(List.of(slot(MEND)), 25000L, 1, 99L);
        assertFalse(session.isDeclined(LETTER, 25000L), "a fresh set is a fresh conversation");
    }

    @Test
    @DisplayName("a configured cooldown is about the clock, so it outlives the set it was given in")
    void configuredCooldownSurvivesAReroll() {
        OfferSession session = session();
        session.decline(LETTER, 1000L, 5000);

        assertTrue(session.isDeclined(LETTER, 4000L));
        session.redraw(List.of(slot(MEND)), 2000L, 1, 99L);
        assertTrue(session.isDeclined(LETTER, 4000L), "a cooldown a server owner configured is not a mood");
        assertFalse(session.isDeclined(LETTER, 6001L), "and it does end");
    }

    @Test
    @DisplayName("lapsed refusals are pruned; open-ended ones are not")
    void pruningKeepsTheMapBounded() {
        OfferSession session = session();
        session.decline(LETTER, 1000L, 100);
        session.decline(MEND, 1000L, 0);

        session.pruneDeclines(2000L);

        assertEquals(1, session.declinedView().size(), () -> "left behind: " + session.declinedView());
        assertTrue(session.isDeclined(MEND, 2000L));
        assertFalse(session.isDeclined(LETTER, 2000L));
    }

    @Test
    @DisplayName("a decline survives a save and load of the player's quest data")
    void declineSurvivesTheSaveRoundTrip() {
        PlayerQuestData data = new PlayerQuestData();
        OfferSession session = data.offers().get(VILLAGER);
        session.redraw(List.of(slot(LETTER), slot(MEND)), 1000L, 1, 4242L);
        session.removeSlot(LETTER);
        session.decline(LETTER, 1000L, 0);
        data.history().recordOutcome(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED);

        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(data.save());

        OfferSession after = reloaded.offers().get(VILLAGER);
        assertTrue(after.isDeclined(LETTER, 1000L), "relogging must not un-decline a quest");
        assertEquals(List.of(MEND), after.slots().stream().map(OfferSession.Slot::questId).toList());
        assertEquals(1, reloaded.history().outcomeCountByGiver(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED));
    }

    @Test
    @DisplayName("declining records its own outcome and touches no completion count")
    void decliningIsFree() {
        QuestHistory history = new QuestHistory();
        history.recordOutcome(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED);

        assertEquals(1, history.outcomeCount(LETTER, QuestHistory.Outcome.DECLINED));
        assertEquals(1, history.outcomeCountByGiver(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED));
        assertEquals(0, history.completionCount(LETTER), "turning an offer down is not finishing it");
        assertEquals(0, history.outcomeCount(LETTER, QuestHistory.Outcome.FAILED),
                "and it is certainly not failing it");
        assertEquals(0, history.outcomeCount(LETTER, QuestHistory.Outcome.ABANDONED));
        assertFalse(history.onCooldown(LETTER, VILLAGER, 1000L), "declining sets no quest cooldown");
    }

    @Test
    @DisplayName("the DECLINED outcome round-trips through the existing history NBT")
    void declinedOutcomeNeedsNoNewSaveFormat() {
        QuestHistory history = new QuestHistory();
        history.recordOutcome(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED);

        QuestHistory reloaded = new QuestHistory();
        reloaded.load(history.save());

        assertEquals(1, reloaded.outcomeCountByGiver(LETTER, VILLAGER, QuestHistory.Outcome.DECLINED));
    }

    @Test
    @DisplayName("frozen template values and the voiced line survive the round trip")
    void frozenSlotContentIsPersisted() {
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        values.put("count", new ResolvedValue.IntValue(7));
        OfferSession session = new OfferSession(VILLAGER);
        Component voiced = Component.literal("Seven, if you can spare them.");
        session.redraw(List.of(new OfferSession.Slot(LETTER, new ResolvedTemplate(values), voiced)),
                1000L, 1, 1L);

        OfferSession reloaded = OfferSession.load(session.save());

        OfferSession.Slot slot = reloaded.slots().get(0);
        assertNotNull(slot.frozenValues(), "the numbers the player was shown must survive a relog");
        assertEquals("7", slot.frozenValues().get("count").map(ResolvedValue::plain).orElse(""));
        assertEquals(voiced, slot.voicedOffer(), "so must what the villager actually said");
    }

    @Test
    @DisplayName("a session is stale when it is empty, aged out, or built against an old datapack")
    void stalenessRules() {
        OfferSession session = session();

        assertFalse(session.isStale(1000L, 24000, 1), "freshly drawn");
        assertFalse(session.isStale(24999L, 24000, 1), "one tick inside the window");
        assertTrue(session.isStale(25000L, 24000, 1), "the window elapsed");
        assertTrue(session.isStale(1000L, 24000, 2), "the datapack was reloaded underneath it");
        assertTrue(session.isStale(999L, 24000, 1), "the clock went backwards; never trap a session");
        assertTrue(new OfferSession(VILLAGER).isStale(1000L, 24000, 1), "nothing drawn yet");
    }

    @Test
    @DisplayName("offerRefreshTicks actually controls how often a set is redrawn")
    void refreshWindowIsHonoured() {
        OfferSession session = session();
        // The key was declared, documented, and read nowhere: the cadence was hardcoded to one MC day.
        // At 6000 the set is due four times a day, which is what the config always claimed it could do.
        assertFalse(session.isStale(5999L, 6000, 1));
        assertTrue(session.isStale(7000L, 6000, 1));
        assertFalse(session.isStale(7000L, 24000, 1), "and the default still means one day");
    }

    @Test
    @DisplayName("sessions nobody has looked at in a long time are dropped, with their refusals")
    void staleSessionsArePruned() {
        OfferSessions sessions = new OfferSessions();
        sessions.get(VILLAGER).redraw(List.of(slot(LETTER)), 0L, 1, 1L);
        UUID recent = UUID.randomUUID();
        sessions.get(recent).redraw(List.of(slot(MEND)), 200_000L, 1, 1L);

        sessions.prune(200_000L, 24000);

        assertEquals(1, sessions.size(), "a player who greets a thousand villagers must not carry a "
                + "thousand remembered menus forever");
        assertTrue(sessions.find(recent).isPresent());
        assertTrue(sessions.find(VILLAGER).isEmpty());
    }

    @Test
    @DisplayName("copyFrom deep-copies, so a respawn does not share a session with the old player data")
    void copyFromIsDeep() {
        OfferSessions original = new OfferSessions();
        original.get(VILLAGER).redraw(List.of(slot(LETTER)), 1000L, 1, 1L);

        OfferSessions copy = new OfferSessions();
        copy.copyFrom(original);
        copy.get(VILLAGER).removeSlot(LETTER);

        assertEquals(1, original.get(VILLAGER).slots().size(),
                "the copy shares a session object with the original");
        assertEquals(0, copy.get(VILLAGER).slots().size());
    }

    @Test
    @DisplayName("an absent offers compound loads as an empty store")
    void preexistingSavesLoadClean() {
        PlayerQuestData data = new PlayerQuestData();
        data.load(new CompoundTag());
        assertEquals(0, data.offers().size());
    }
}
