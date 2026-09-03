package dev.otectus.mcaquests;

import dev.otectus.mcaquests.support.TestPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every config option must change something.
 *
 * <p>Six did not. {@code offerRefreshTicks}, {@code enableDefaultQuestPack},
 * {@code defaultQuestCooldownTicks}, {@code requireOriginalVillagerForTurnIn},
 * {@code maxConcurrentProjectsPerScope} and {@code compat.townstead.pollIntervalTicks} were all declared,
 * all written into the generated TOML, all documented in {@code CONFIG.md} with a description of what they
 * did — and read by nothing at all. A server owner could set {@code offerRefreshTicks} to 6000 expecting
 * four offer rotations a day and get exactly the behaviour they had before, with no error, no warning, and
 * nothing in the log.
 *
 * <p>That is a worse failure than an option that does the wrong thing, because there is no way to notice
 * it from outside the source. This test is the noticing: a field on the config spec that no other file
 * mentions fails the build.
 *
 * <p>A source scan rather than a reflective call graph, because a config value is reached through a
 * {@code COMMON.name.get()} chain that no runtime inspection can follow. The check is therefore coarse —
 * a mention in a comment would satisfy it — but coarse in the safe direction: it cannot pass a key that
 * nothing anywhere refers to, which is the failure mode that actually happened six times.
 */
class DeadConfigTest {

    private static final Path MAIN = TestPaths.of("src/main/java");
    private static final Path CONFIG =
            MAIN.resolve("dev/otectus/mcaquests/McaQuestsConfig.java");

    @Test
    @DisplayName("every declared config option is read somewhere outside McaQuestsConfig")
    void noConfigOptionIsDeclaredAndIgnored() {
        List<String> names = declaredOptions();
        assertTrue(names.size() > 60,
                "the reflection over the config spec found almost nothing; the class has been reshaped");

        String sources = allSourcesOutsideConfig();
        List<String> dead = names.stream().filter(name -> !sources.contains(name)).sorted().toList();

        assertEquals(List.of(), dead, "these config options are declared, documented and read by nothing; "
                + "either wire them up or delete them, along with their CONFIG.md rows");
    }

    /** Every {@code ModConfigSpec.*Value} field on both spec holders, by field name. */
    private static List<String> declaredOptions() {
        List<String> names = new ArrayList<>();
        for (Object holder : List.of(McaQuestsConfig.COMMON, McaQuestsConfig.CLIENT)) {
            for (Field field : holder.getClass().getDeclaredFields()) {
                if (ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                    names.add(field.getName());
                }
            }
        }
        return names;
    }

    /**
     * Every main source file except the config class itself, concatenated.
     *
     * <p>One string rather than a per-file scan because the question is only "is this name mentioned
     * anywhere", and a single {@code contains} over the concatenation answers it without the bookkeeping.
     */
    private static String allSourcesOutsideConfig() {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.equals(CONFIG)) {
                    continue;
                }
                all.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return all.toString();
    }
}
