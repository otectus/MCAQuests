package dev.otectus.mcaquests;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every config key has a row in CONFIG.md (1.7.0).
 *
 * <p>CLAUDE.md and CONFIG.md both say every key is documented there, and until 1.7.0 the eleven project
 * keys were documented only in DATAPACK.md. {@link DeadConfigTest} catches a key nothing reads; this catches
 * a key nobody can look up. The keys are read from the spec's own {@code define} calls, so a key added
 * without a row fails here, not in a server owner's search.
 */
class ConfigDocumentedTest {

    private static final Pattern DEFINE = Pattern.compile(
            "\\.define(?:InRange|List|ListAllowEmpty|Enum)?\\(\\s*\"([A-Za-z0-9_]+)\"");

    @Test
    @DisplayName("every key the config spec defines appears in CONFIG.md")
    void everyKeyIsDocumented() throws Exception {
        String spec = Files.readString(TestPaths.of("src", "main", "java", "dev", "otectus", "mcaquests",
                "McaQuestsConfig.java"));
        String doc = Files.readString(TestPaths.of("CONFIG.md"));
        List<String> keys = new ArrayList<>();
        Matcher matcher = DEFINE.matcher(spec);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        assertTrue(keys.size() > 100, "the define pattern no longer matches McaQuestsConfig: " + keys.size());
        List<String> missing = keys.stream().filter(key -> !doc.contains("`" + key + "`")).sorted().toList();
        assertEquals(List.of(), missing, "config keys with no row in CONFIG.md");
    }
}
