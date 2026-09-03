package dev.otectus.mcaquests;

import dev.otectus.mcaquests.network.JournalArchiveEntry;
import dev.otectus.mcaquests.quest.JournalService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry-free tests for the pure journal archive builder (0.7.0). */
class JournalSnapshotTest {

    private static final Function<ResourceLocation, Component> RESOLVER = id -> Component.literal(id.getPath());

    @Test
    void archiveSortsByCountThenId() {
        Map<ResourceLocation, Integer> completions = new LinkedHashMap<>();
        completions.put(id("alpha"), 2);
        completions.put(id("bravo"), 5);
        completions.put(id("charlie"), 2);

        List<JournalArchiveEntry> archive = JournalService.buildArchive(completions, RESOLVER, 100);
        assertEquals(3, archive.size());
        assertEquals("bravo", archive.get(0).questTitle().getString());
        // Tie on count=2 broken by id ascending: alpha before charlie.
        assertEquals("alpha", archive.get(1).questTitle().getString());
        assertEquals("charlie", archive.get(2).questTitle().getString());
        assertEquals(5, archive.get(0).count());
    }

    @Test
    void archiveCapLimitsEntries() {
        Map<ResourceLocation, Integer> completions = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            completions.put(id("q" + i), i + 1);
        }
        List<JournalArchiveEntry> archive = JournalService.buildArchive(completions, RESOLVER, 3);
        assertEquals(3, archive.size());
        // Highest counts kept: q9 (10), q8 (9), q7 (8).
        assertEquals("q9", archive.get(0).questTitle().getString());
        assertEquals("q7", archive.get(2).questTitle().getString());
    }

    @Test
    void archiveDropsZeroAndNullCounts() {
        Map<ResourceLocation, Integer> completions = new LinkedHashMap<>();
        completions.put(id("zero"), 0);
        completions.put(id("real"), 1);
        List<JournalArchiveEntry> archive = JournalService.buildArchive(completions, RESOLVER, 100);
        assertEquals(1, archive.size());
        assertEquals("real", archive.get(0).questTitle().getString());
    }

    @Test
    void emptyCompletionsYieldEmptyArchive() {
        assertTrue(JournalService.buildArchive(Map.of(), RESOLVER, 100).isEmpty());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mcaquests", path);
    }
}
