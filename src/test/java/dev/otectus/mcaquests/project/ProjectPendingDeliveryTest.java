package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectInstanceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle of the transient state {@code ProjectManager} keeps between contributions and delivery
 * passes. Everything here is static per-server state, so each test starts from a cleared manager.
 */
class ProjectPendingDeliveryTest {

    private static final ResourceLocation PROJECT = new ResourceLocation("test", "project");
    private static final UUID PLAYER = UUID.randomUUID();

    private static ProjectInstanceKey key(int village) {
        return new ProjectInstanceKey(PROJECT, ProjectScope.VILLAGE, "v:" + village);
    }

    @BeforeEach
    void reset() {
        ProjectManager.clearSessionState();
    }

    @Test
    void throttleGatesExpireInsteadOfAccumulating() {
        for (int i = 0; i < 500; i++) {
            ProjectManager.recordContribution(UUID.randomUUID(), key(i), i, 20);
        }
        assertTrue(ProjectManager.throttleGateCount() <= 64,
                "expired gates must be pruned, saw " + ProjectManager.throttleGateCount());
        assertTrue(ProjectManager.throttleGateCount() > 0, "the newest gate is still live");
    }

    @Test
    void gatesFromAWorldWithAHigherGameTimeAreDropped() {
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            ProjectManager.recordContribution(player, key(i), 1_000_000L, 20);
        }
        // Same JVM, different world: game time restarts low, so every stored tick is now in the future.
        ProjectManager.recordContribution(player, key(999), 5L, 20);
        assertEquals(1, ProjectManager.throttleGateCount());
    }

    @Test
    void clearSessionStateEmptiesTheThrottleCache() {
        ProjectManager.recordContribution(UUID.randomUUID(), key(1), 10L, 100);
        ProjectManager.recordContribution(UUID.randomUUID(), key(2), 12L, 100);
        assertEquals(2, ProjectManager.throttleGateCount());
        ProjectManager.clearSessionState();
        assertEquals(0, ProjectManager.throttleGateCount());
    }

    @Test
    void bankedRewardsAreDeliveredEvenWhenVillageProjectsAreDisabled() {
        PendingReward banked = PendingReward.ofBanked(BankedReward.reputation(5));
        PendingReward phase = PendingReward.ofPhase(PROJECT, 0, 0);
        List<PendingReward> attempted = new ArrayList<>();

        List<PendingReward> retained = ProjectManager.drainPass(PLAYER, List.of(banked, phase), false,
                reward -> {
                    attempted.add(reward);
                    return true;
                });

        assertEquals(List.of(banked), attempted, "only the banked debt is paid while projects are off");
        assertEquals(List.of(phase), retained, "the phase reward is held, never discarded");
    }

    @Test
    void anUndeliverableEntrySurvivesThePass() {
        PendingReward banked = PendingReward.ofBanked(BankedReward.hearts(2, "SPOUSE"));

        List<PendingReward> retained = ProjectManager.drainPass(PLAYER, List.of(banked), true,
                reward -> false);

        assertEquals(List.of(banked), retained);
    }
}
