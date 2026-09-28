package dev.otectus.mcaquests;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No data directory still uses a name Minecraft 1.21 renamed.
 *
 * <p>1.21 reads tags from {@code tags/<registry>} ({@code tags/item}, {@code tags/entity_type}) and its
 * other data from singular folders ({@code recipe}, {@code loot_table}, ...). A file left under the
 * 1.20.1 name is not an error: the game never looks there, so a tag reads empty and an objective that
 * names it can never advance, with only an "empty tag" warning in the log. The 1.20.1 layout was carried
 * over for four tags in this port (harbor catch, pottery sherds, common undead and the Ice and Fire
 * dread), which left the quests and the situation that use them unwinnable on NeoForge until 1.7.1.
 *
 * <p>Checks the mod's own {@code data/} tree and every embedded compat pack's, from the source tree.
 */
class DataDirectoryLayoutTest {

    /** Folders directly under {@code data/<namespace>/} that 1.21 renamed. */
    private static final Set<String> RENAMED_ROOTS = Set.of("recipes", "loot_tables", "advancements",
            "structures", "predicates", "item_modifiers", "functions");

    /** Folders directly under {@code data/<namespace>/tags/} that 1.21 renamed. */
    private static final Set<String> RENAMED_TAGS = Set.of("items", "blocks", "entity_types", "fluids",
            "game_events", "functions");

    @Test
    void noDataDirectoryUsesAPre121Name() throws IOException {
        List<Path> dataRoots = new ArrayList<>();
        dataRoots.add(TestPaths.of("src/main/resources/data"));
        Path compatPacks = TestPaths.of("src/main/resources/compatpacks");
        if (Files.isDirectory(compatPacks)) {
            try (Stream<Path> packs = Files.list(compatPacks)) {
                packs.map(pack -> pack.resolve("data")).filter(Files::isDirectory).forEach(dataRoots::add);
            }
        }
        assertTrue(Files.isDirectory(dataRoots.get(0)), "no data/ tree at " + dataRoots.get(0));

        List<String> stale = new ArrayList<>();
        for (Path data : dataRoots) {
            try (Stream<Path> namespaces = Files.list(data)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> children = Files.list(namespace)) {
                        children.filter(Files::isDirectory)
                                .filter(dir -> RENAMED_ROOTS.contains(dir.getFileName().toString()))
                                .forEach(dir -> stale.add(TestPaths.projectRoot().relativize(dir).toString()));
                    }
                    Path tags = namespace.resolve("tags");
                    if (Files.isDirectory(tags)) {
                        try (Stream<Path> children = Files.list(tags)) {
                            children.filter(Files::isDirectory)
                                    .filter(dir -> RENAMED_TAGS.contains(dir.getFileName().toString()))
                                    .forEach(dir -> stale.add(TestPaths.projectRoot().relativize(dir).toString()));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), stale, "1.21 never reads these folders, so everything in them is silently absent");
    }
}
