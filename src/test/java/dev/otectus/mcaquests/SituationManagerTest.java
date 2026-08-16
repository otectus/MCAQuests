package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the situation open core ({@code SituationManager.tryOpen}): the throttle,
 * dedupe guard, and cooldown bookkeeping, exercised against an in-memory {@link SituationSavedData}
 * without bootstrapping any registry-backed definition (0.8.0).
 */
class SituationManagerTest {

    private static final ResourceLocation DEF_A = new ResourceLocation("mcaquests:after_raid");
    private static final ResourceLocation DEF_B = new ResourceLocation("mcaquests:cure_infected");
    private static final ResourceLocation DEF_C = new ResourceLocation("mcaquests:night_watch");

    private static Optional<SituationInstance> open(SituationSavedData data, ResourceLocation def, int village,
                                                    long now, int maxConcurrent, long globalCd,
                                                    int duration, int cooldown) {
        UUID id = UUID.randomUUID();
        return SituationManager.tryOpen(data, def, duration, cooldown, village, null, null,
                now, maxConcurrent, globalCd, id, id.getMostSignificantBits());
    }

    @Test
    void opensAndWritesCooldownsAndDeadline() {
        SituationSavedData data = new SituationSavedData();
        Optional<SituationInstance> opened = open(data, DEF_A, 5, 1000L, 2, 6000L, 24000, 12000);

        assertTrue(opened.isPresent());
        assertEquals(1, data.openCountInVillage(5));
        assertEquals(1000L + 24000L, opened.get().deadlineGameTime());
        assertEquals(1000L + 12000L, data.cooldownUntil(5, DEF_A));
        assertEquals(1000L + 6000L, data.globalCooldownUntil(5));
    }

    @Test
    void doesNotReopenWhileSameDefAlreadyOpen() {
        SituationSavedData data = new SituationSavedData();
        // global cooldown 0 so only the dedupe guard can block the re-open.
        assertTrue(open(data, DEF_A, 5, 1000L, 5, 0L, 24000, 0).isPresent());
        assertFalse(open(data, DEF_A, 5, 1000L, 5, 0L, 24000, 0).isPresent());
        assertEquals(1, data.openCountInVillage(5));
    }

    @Test
    void respectsConcurrencyCap() {
        SituationSavedData data = new SituationSavedData();
        // global cooldown 0 so the cap (not the global anti-spam) is what blocks the third open.
        assertTrue(open(data, DEF_A, 5, 1000L, 2, 0L, 24000, 0).isPresent());
        assertTrue(open(data, DEF_B, 5, 1000L, 2, 0L, 24000, 0).isPresent());
        assertFalse(open(data, DEF_C, 5, 1000L, 2, 0L, 24000, 0).isPresent());
        assertEquals(2, data.openCountInVillage(5));
    }

    @Test
    void globalCooldownBlocksAnyFurtherOpenSameTick() {
        SituationSavedData data = new SituationSavedData();
        assertTrue(open(data, DEF_A, 5, 1000L, 5, 6000L, 24000, 0).isPresent());
        // different def, under the cap, but the global anti-spam window is active.
        assertFalse(open(data, DEF_B, 5, 1000L, 5, 6000L, 24000, 0).isPresent());
        // a different village is independent of village 5's global cooldown.
        assertTrue(open(data, DEF_B, 9, 1000L, 5, 6000L, 24000, 0).isPresent());
    }

    @Test
    void perDefinitionCooldownBlocksReopenUntilElapsed() {
        SituationSavedData data = new SituationSavedData();
        Optional<SituationInstance> first = open(data, DEF_A, 5, 1000L, 5, 0L, 24000, 5000);
        assertTrue(first.isPresent());
        // Close it so the dedupe guard no longer applies; only the per-def cooldown should matter.
        data.removeInstance(first.get().instanceId());

        assertFalse(open(data, DEF_A, 5, 3000L, 5, 0L, 24000, 5000).isPresent()); // 3000 < 6000
        assertTrue(open(data, DEF_A, 5, 6000L, 5, 0L, 24000, 5000).isPresent());  // cooldown elapsed
    }
}
