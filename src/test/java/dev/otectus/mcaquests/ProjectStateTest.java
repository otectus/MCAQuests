package dev.otectus.mcaquests;

import dev.otectus.mcaquests.data.GraphCycles;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.ProjectInstanceKey;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for shared project storage: NBT round-trips, idempotency latch, and cycle detection. */
class ProjectStateTest {

    @Test
    void sharedObjectiveProgressRoundTrips() {
        SharedObjectiveProgress progress = new SharedObjectiveProgress();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        progress.add(5);
        progress.addContribution(a, 3);
        progress.addContribution(b, 2);
        assertTrue(progress.markTalkedTo(a));
        assertFalse(progress.markTalkedTo(a)); // dedupe

        SharedObjectiveProgress loaded = SharedObjectiveProgress.load(progress.save());
        assertEquals(5, loaded.count());
        assertEquals(3, loaded.contributionOf(a));
        assertEquals(2, loaded.contributionOf(b));
        assertTrue(loaded.hasTalkedTo(a));
    }

    @Test
    void projectStateRoundTripsAndAdvances() {
        UUID sponsor = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProjectState state = new ProjectState(new ResourceLocation("mcaquests:well_repair"),
                ProjectScope.VILLAGE, "v:12", new ResourceLocation("minecraft:overworld"),
                new BlockPos(10, 64, -20), OptionalInt.of(12), 1000L, 2);
        state.addSponsor(sponsor);
        state.addParticipant(player);
        state.progress(0).add(40);
        state.progress(0).addContribution(player, 40);
        assertTrue(state.tryMarkPhaseDistributed(0));
        assertFalse(state.tryMarkPhaseDistributed(0)); // one-shot guard
        state.setStatus(ProjectStatus.PAUSED);

        CompoundTag tag = state.save();
        ProjectState loaded = ProjectState.load(tag);
        assertEquals("v:12", loaded.identity());
        assertEquals(ProjectScope.VILLAGE, loaded.scope());
        assertEquals(OptionalInt.of(12), loaded.villageId());
        assertEquals(new BlockPos(10, 64, -20), loaded.anchorPos());
        assertEquals(ProjectStatus.PAUSED, loaded.status());
        assertEquals(40, loaded.progress(0).count());
        assertEquals(40, loaded.progress(0).contributionOf(player));
        assertTrue(loaded.sponsors().contains(sponsor));
        assertTrue(loaded.participants().contains(player));
        assertFalse(loaded.tryMarkPhaseDistributed(0)); // distributed flag survived the round-trip

        // enterPhase resizes shared progress to the next phase's objective count.
        loaded.enterPhase(1, 1);
        assertEquals(1, loaded.currentPhase());
        assertEquals(1, loaded.progressCount());
        assertEquals(0, loaded.progress(0).count());
    }

    @Test
    void instanceKeyRoundTrips() {
        ProjectInstanceKey key = new ProjectInstanceKey(new ResourceLocation("mcaquests:guardhouse_stockpile"),
                ProjectScope.PROFESSION, "p:12:minecraft:librarian");
        Optional<ProjectInstanceKey> parsed = ProjectInstanceKey.parse(key.asString());
        assertTrue(parsed.isPresent());
        assertEquals(key, parsed.get());
        assertEquals("p:12:minecraft:librarian", parsed.get().identity());
    }

    @Test
    void graphCyclesDetectsFollowUpLoops() {
        ResourceLocation a = new ResourceLocation("mcaquests:a");
        ResourceLocation b = new ResourceLocation("mcaquests:b");
        ResourceLocation c = new ResourceLocation("mcaquests:c");
        assertTrue(GraphCycles.findCycle(Map.of(a, List.of(b), b, List.of(c), c, List.of())).isEmpty());
        assertTrue(GraphCycles.findCycle(Map.of(a, List.of(b), b, List.of(a))).isPresent());
    }
}
