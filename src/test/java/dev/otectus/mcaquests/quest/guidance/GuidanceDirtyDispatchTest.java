package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two halves of the event-driven guidance model: what gets recomputed, and what gets sent.
 *
 * <p>Both are rules that used to be implicit in a once-a-second poll. Marking has to coalesce, or a
 * turn-in that finishes a chain recomputes the same player three times in one tick; and the equality
 * suppression has to be exactly "is this news", or the first snapshot after a death — byte-identical
 * to the one before it — is silently dropped by the very optimisation that makes the poll free.
 */
class GuidanceDirtyDispatchTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private final GuidanceDirtySet dirty = new GuidanceDirtySet();

    @Test
    @DisplayName("several marks for one player are drained once")
    void marksCoalesce() {
        UUID player = UUID.randomUUID();

        dirty.mark(player);
        dirty.mark(player);
        dirty.mark(player);

        assertEquals(Set.of(player), dirty.drain());
    }

    @Test
    @DisplayName("a drain empties the set")
    void drainEmpties() {
        UUID player = UUID.randomUUID();
        dirty.mark(player);

        dirty.drain();

        assertTrue(dirty.isEmpty(), "a drained mark must not be answered twice");
        assertEquals(Set.of(), dirty.drain());
    }

    @Test
    @DisplayName("two players are drained together")
    void keepsPlayersApart() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        dirty.mark(first);
        dirty.mark(second);

        assertEquals(Set.of(first, second), dirty.drain());
    }

    @Test
    @DisplayName("an unchanged snapshot is not sent again")
    void suppressesEqualSnapshots() {
        UUID player = UUID.randomUUID();
        GuidanceSnapshot snapshot = snapshot("mcaquests:deliver", 10, 64, 10);

        assertTrue(GuidanceService.shouldSend(player, snapshot), "the first snapshot is always news");
        GuidanceService.remember(player, snapshot);

        assertFalse(GuidanceService.shouldSend(player, snapshot("mcaquests:deliver", 10, 64, 10)),
                "an equal snapshot is the whole reason the once-a-second pass costs no packets");
    }

    @Test
    @DisplayName("a moved destination is sent")
    void sendsChangedSnapshots() {
        UUID player = UUID.randomUUID();
        GuidanceService.remember(player, snapshot("mcaquests:deliver", 10, 64, 10));

        assertTrue(GuidanceService.shouldSend(player, snapshot("mcaquests:deliver", 11, 64, 10)),
                "a destination that moved is news");
    }

    @Test
    @DisplayName("an empty snapshot after a real one is sent")
    void sendsTheEmptySnapshot() {
        UUID player = UUID.randomUUID();
        GuidanceService.remember(player, snapshot("mcaquests:deliver", 10, 64, 10));

        assertTrue(GuidanceService.shouldSend(player, GuidanceSnapshot.EMPTY),
                "taking every marker away is a message, not the absence of one");
    }

    @Test
    @DisplayName("forgetting a player un-suppresses their next snapshot")
    void forgettingResendsFromScratch() {
        UUID player = UUID.randomUUID();
        GuidanceSnapshot snapshot = snapshot("mcaquests:deliver", 10, 64, 10);
        GuidanceService.remember(player, snapshot);

        GuidanceService.forget(player);

        assertTrue(GuidanceService.shouldSend(player, snapshot),
                "this is what makes the first snapshot after a respawn arrive");
    }

    private static GuidanceSnapshot snapshot(String questId, int x, int y, int z) {
        GuidanceTarget target = new GuidanceTarget(GuidanceKind.VILLAGER, OptionalInt.empty(),
                new BlockPos(x, y, z), Level.OVERWORLD, Component.literal("Anna"),
                3, false, false, 1.95F);
        return new GuidanceSnapshot(
                List.of(new ActiveGuidance(ResourceLocation.parse(questId), new UUID(1L, 2L), target)), 0);
    }
}
