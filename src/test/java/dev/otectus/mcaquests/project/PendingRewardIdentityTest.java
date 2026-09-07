package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PendingRewardIdentityTest {
    private static final ResourceLocation PROJECT = new ResourceLocation("test", "project");
    private static final UUID PLAYER = UUID.randomUUID();

    private static ProjectState state(int village) {
        ProjectState state = new ProjectState(PROJECT, ProjectScope.VILLAGE, "v:" + village,
                new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO,
                OptionalInt.of(village), 0, 1);
        state.addParticipant(PLAYER);
        state.tryMarkPhaseDistributed(0);
        return state;
    }

    @Test void queuedRewardsKeepTheirVillageAcrossSaveLoad() {
        ProjectState home = state(1);
        ProjectState elsewhere = state(2);
        PendingReward loaded = PendingReward.load(PendingReward.ofPhase(home, 0, 0).save()).orElseThrow();
        assertTrue(loaded.matchesInstance(home, PLAYER));
        assertFalse(loaded.matchesInstance(elsewhere, PLAYER));
    }

    @Test void legacyShapeIsPreservedAndOnlyMatchesActualParticipation() {
        PendingReward legacy = PendingReward.ofPhase(PROJECT, 0, 0);
        assertFalse(legacy.save().contains("instance"));
        assertEquals(legacy, PendingReward.load(legacy.save()).orElseThrow());
        assertTrue(legacy.matchesInstance(state(1), PLAYER));
        assertFalse(legacy.matchesInstance(state(1), UUID.randomUUID()));
    }

    @Test void replacingTheLiveInstanceCannotChangeAnAlreadyBankedPayout() {
        ProjectState original = state(1);
        original.freezeReward(0, 0, 9);
        PendingReward pending = PendingReward.load(PendingReward.ofPhase(original, 0, 0).save()).orElseThrow();
        ProjectState replacement = state(1);
        replacement.freezeReward(0, 0, 99);
        ProjectState saved = ProjectState.load(pending.instanceSnapshot());
        assertEquals(original.key(), replacement.key(), "a reset reuses the same scope key");
        assertEquals(9, saved.frozenReward(0, 0).orElseThrow());
        assertEquals(99, replacement.frozenReward(0, 0).orElseThrow());
    }
}
