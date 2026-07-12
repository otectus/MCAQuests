package dev.otectus.mcaquests;

import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.ProgressionStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry-free NBT round-trips + increment semantics for {@link ProgressionStats} (spec section 11.2),
 * including the legacy/absent-key back-compat case.
 */
class ProgressionStatsCodecTest {

    private static final ResourceLocation SITUATION_A = new ResourceLocation("mcaquests", "raid_defense");
    private static final ResourceLocation SITUATION_B = new ResourceLocation("mcaquests", "food_shortage");
    private static final ResourceLocation PROJECT_A = new ResourceLocation("mcaquests", "build_well");
    private static final ResourceLocation PROJECT_B = new ResourceLocation("mcaquests", "repair_walls");

    @Test
    void incrementAddsAndSums() {
        ProgressionStats stats = new ProgressionStats();
        ProgressionStats.increment(stats.situationSuccesses(), SITUATION_A, 1);
        ProgressionStats.increment(stats.situationSuccesses(), SITUATION_A, 1);
        ProgressionStats.increment(stats.situationSuccesses(), SITUATION_B, 1);

        assertEquals(2, ProgressionStats.count(stats.situationSuccesses(), SITUATION_A));
        assertEquals(1, ProgressionStats.count(stats.situationSuccesses(), SITUATION_B));
        assertEquals(3, ProgressionStats.total(stats.situationSuccesses()));
    }

    @Test
    void countOfUnknownKeyIsZero() {
        ProgressionStats stats = new ProgressionStats();
        assertEquals(0, ProgressionStats.count(stats.projectCompletions(), PROJECT_A));
        assertEquals(0, ProgressionStats.total(stats.projectCompletions()));
    }

    @Test
    void contributionsSumArbitraryAmounts() {
        ProgressionStats stats = new ProgressionStats();
        ProgressionStats.increment(stats.projectContributions(), PROJECT_A, 5);
        ProgressionStats.increment(stats.projectContributions(), PROJECT_A, 3);
        ProgressionStats.increment(stats.projectContributions(), PROJECT_B, 10);

        assertEquals(8, ProgressionStats.count(stats.projectContributions(), PROJECT_A));
        assertEquals(10, ProgressionStats.count(stats.projectContributions(), PROJECT_B));
        assertEquals(18, ProgressionStats.total(stats.projectContributions()));
    }

    @Test
    void nbtRoundTripAllThreeMapsMultipleKeys() {
        ProgressionStats stats = new ProgressionStats();
        ProgressionStats.increment(stats.situationSuccesses(), SITUATION_A, 2);
        ProgressionStats.increment(stats.situationSuccesses(), SITUATION_B, 1);
        ProgressionStats.increment(stats.projectCompletions(), PROJECT_A, 1);
        ProgressionStats.increment(stats.projectCompletions(), PROJECT_B, 4);
        ProgressionStats.increment(stats.projectContributions(), PROJECT_A, 7);
        ProgressionStats.increment(stats.projectContributions(), PROJECT_B, 12);

        ProgressionStats loaded = new ProgressionStats();
        loaded.load(stats.save());

        assertEquals(2, ProgressionStats.count(loaded.situationSuccesses(), SITUATION_A));
        assertEquals(1, ProgressionStats.count(loaded.situationSuccesses(), SITUATION_B));
        assertEquals(1, ProgressionStats.count(loaded.projectCompletions(), PROJECT_A));
        assertEquals(4, ProgressionStats.count(loaded.projectCompletions(), PROJECT_B));
        assertEquals(7, ProgressionStats.count(loaded.projectContributions(), PROJECT_A));
        assertEquals(12, ProgressionStats.count(loaded.projectContributions(), PROJECT_B));
    }

    @Test
    void emptyCompoundLoadsAsEmpty() {
        ProgressionStats stats = new ProgressionStats();
        stats.load(new CompoundTag());
        assertTrue(stats.isEmpty());
    }

    @Test
    void playerQuestDataRoundTripWithAndWithoutStats() {
        PlayerQuestData withStats = new PlayerQuestData();
        ProgressionStats.increment(withStats.stats().situationSuccesses(), SITUATION_A, 3);
        ProgressionStats.increment(withStats.stats().projectCompletions(), PROJECT_A, 2);
        ProgressionStats.increment(withStats.stats().projectContributions(), PROJECT_B, 6);

        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(withStats.save());
        assertEquals(3, ProgressionStats.count(reloaded.stats().situationSuccesses(), SITUATION_A));
        assertEquals(2, ProgressionStats.count(reloaded.stats().projectCompletions(), PROJECT_A));
        assertEquals(6, ProgressionStats.count(reloaded.stats().projectContributions(), PROJECT_B));

        // A pre-1.0.0 save with no "stats" tag must load cleanly as empty (back-compat).
        PlayerQuestData legacy = new PlayerQuestData();
        CompoundTag legacyTag = new CompoundTag();
        legacy.load(legacyTag);
        assertTrue(legacy.stats().isEmpty());
    }

    @Test
    void copyFromCopiesStats() {
        PlayerQuestData source = new PlayerQuestData();
        ProgressionStats.increment(source.stats().situationSuccesses(), SITUATION_A, 4);

        PlayerQuestData dest = new PlayerQuestData();
        dest.copyFrom(source);
        assertEquals(4, ProgressionStats.count(dest.stats().situationSuccesses(), SITUATION_A));

        // Mutating the copy must not affect the source (deep copy, not shared map reference).
        ProgressionStats.increment(dest.stats().situationSuccesses(), SITUATION_A, 1);
        assertEquals(4, ProgressionStats.count(source.stats().situationSuccesses(), SITUATION_A));
    }
}
