package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which quest carries the marker, and — the part this exists for — that a quest with nothing to say
 * never stops the search.
 *
 * <p>That was a real bug, of exactly the shape the fall-through was introduced to fix one level up.
 * Guidance counted an objective as "the answer" when it produced <em>either</em> a place <em>or</em> a
 * villager to outline, then sent the place; so an objective that knew a person but not where to find
 * one sent an <b>empty</b> payload — which is how a marker is taken away — and returned without asking
 * anything else. An unstaged {@code escort_entity} is precisely that combination before its first poll
 * freezes the destination, and permanently when the destination anchor never resolves. One escort
 * could switch the marker off for every other quest the player held.
 *
 * <p>The selection rule lives on {@link GuidanceSnapshot} rather than inside {@code GuidanceService}
 * for this reason: it is a rule about a list of answers, not about the world, so it can be exercised
 * without a running server. The service's job is reduced to computing the answers and handing them
 * over.
 */
class GuidanceSelectionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static ActiveGuidance quest(String id) {
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.STRUCTURE, OptionalInt.empty(),
                new BlockPos(100, 64, 100), Level.OVERWORLD, Component.literal(id), 24, true, false,
                0.0F);
        return new ActiveGuidance(ResourceLocation.fromNamespaceAndPath("mcaquests", id), UUID.randomUUID(), target);
    }

    @Test
    @DisplayName("a quest that cannot say where to go does not silence the ones that can")
    void anEmptyAnswerDoesNotEndTheSearch() {
        ActiveGuidance fortress = quest("nether_relay");

        // The player is following the escort (index 0), and it has nothing to point at.
        GuidanceSnapshot snapshot =
                GuidanceSnapshot.select(List.of(Optional.empty(), Optional.of(fortress)), 0);

        assertEquals(List.of(fortress), snapshot.all(),
                "the quest that could answer must still reach the tracker");
        assertEquals(Optional.of(fortress), snapshot.primaryGuidance(),
                "and must take the marker, rather than the marker being taken away");
    }

    @Test
    @DisplayName("the followed quest takes the marker when it can answer")
    void followedQuestWins() {
        ActiveGuidance first = quest("echoes_below");
        ActiveGuidance followed = quest("trial_by_fire");

        GuidanceSnapshot snapshot =
                GuidanceSnapshot.select(List.of(Optional.of(first), Optional.of(followed)), 1);

        assertEquals(List.of(first, followed), snapshot.all(),
                "both rows get a destination; only the beam is exclusive");
        assertEquals(Optional.of(followed), snapshot.primaryGuidance());
    }

    @Test
    @DisplayName("with nothing followed, the first quest that can answer takes the marker")
    void firstAnswerWinsWhenNothingIsFollowed() {
        ActiveGuidance second = quest("drowned_ledger");

        GuidanceSnapshot snapshot = GuidanceSnapshot.select(
                List.of(Optional.empty(), Optional.of(second), Optional.of(quest("relic_hunt"))), -1);

        assertEquals(Optional.of(second), snapshot.primaryGuidance());
    }

    @Test
    @DisplayName("a quest with nothing to say is left out, not left empty")
    void placelessQuestsAreAbsent() {
        // The tracker draws a destination under the rows that have one and nothing under the rest, so
        // "no answer" must not travel as an entry the client has to recognise and skip.
        GuidanceSnapshot snapshot = GuidanceSnapshot.select(
                List.of(Optional.empty(), Optional.empty()), 0);

        assertSame(GuidanceSnapshot.EMPTY, snapshot);
        assertTrue(snapshot.isEmpty());
        assertEquals(Optional.empty(), snapshot.primaryGuidance());
    }

    @Test
    @DisplayName("the marker index survives the quests before it being dropped")
    void primaryIndexIsIntoTheFilteredList() {
        // The bug this guards: indexing the sent list by the quest's position among *all* active
        // quests rather than among the ones that answered would put the beam on the wrong quest as
        // soon as any earlier quest had nothing to say.
        ActiveGuidance followed = quest("relic_beneath_the_well");

        GuidanceSnapshot snapshot = GuidanceSnapshot.select(
                List.of(Optional.empty(), Optional.empty(), Optional.of(followed)), 2);

        assertEquals(0, snapshot.primary());
        assertEquals(Optional.of(followed), snapshot.primaryGuidance());
    }

    @Test
    @DisplayName("an out-of-range marker index reads as 'nothing marked' rather than throwing")
    void outOfRangePrimaryIsClamped() {
        // It arrives over a network, so it is not trusted. A snapshot of destinations with no beam
        // among them is a legal state anyway: every quest's focus could be one the marker skips.
        GuidanceSnapshot snapshot = new GuidanceSnapshot(List.of(quest("last_banner_home")), 7);

        assertEquals(-1, snapshot.primary());
        assertEquals(Optional.empty(), snapshot.primaryGuidance());
    }
}
