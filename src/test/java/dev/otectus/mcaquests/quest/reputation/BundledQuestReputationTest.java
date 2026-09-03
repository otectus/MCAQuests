package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.support.TestPaths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.McaQuestsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Content that <em>requires</em> village standing must ship alongside content that <em>produces</em>
 * it.
 *
 * <p>It barely did. Ten of the 262 bundled quests carried an {@code mcaquests:village_reputation}
 * reward worth 8–12 points; the other 252 were worth nothing, and <b>not one</b> used the
 * {@code reputation} block DATAPACK.md documents for exactly this. Seven quests are gated on standing
 * and six of those seven are themselves among the paying ten, so an ordinary village playthrough had
 * roughly four quests and about 42 points to reach a ladder that puts Acquaintance at 25, Friend at
 * 75, Honored at 150 and Revered at 300. A player reported it as "no matter what I do I have 25 more
 * to acquaintance and my rank is stranger", which is precisely correct arithmetic over a score almost
 * nothing was adding to.
 *
 * <p>{@code QuestManager.grantQuestReputation} returned in silence for each of those 252, so there was
 * no log line, no warning, and no way to see it from inside the game. This test is the seeing: it is a
 * content check, not a code check, and it fails on any pack state where finishing a bundled quest is
 * worth nothing.
 *
 * <p>Reads the shipped JSON from {@code src/main/resources} rather than a loaded registry, so it needs
 * no game, no datapack reload and no MCA — the same discipline as {@code DeadConfigTest} and
 * {@code DialogueStateCoverageTest}.
 */
class BundledQuestReputationTest {

    private static final Path QUESTS =
            TestPaths.of("src/main/resources/data/mcaquests/mcaquests/quests");
    private static final Path PROJECTS =
            TestPaths.of("src/main/resources/data/mcaquests/mcaquests/projects");

    private record Quest(Path path, JsonObject json) {

        String id() {
            return json.has("id") ? json.get("id").getAsString() : path.getFileName().toString();
        }

        /** The declared difficulty band, lowercased, or empty when the quest omits it. */
        String difficulty() {
            return json.has("difficulty")
                    ? json.get("difficulty").getAsString().toLowerCase(Locale.ROOT)
                    : "";
        }

        boolean authorsReputation() {
            if (json.has("reputation")) {
                return true;
            }
            JsonArray rewards = json.getAsJsonArray("rewards");
            if (rewards == null) {
                return false;
            }
            for (JsonElement reward : rewards) {
                if (reward.isJsonObject() && reward.getAsJsonObject().has("type")
                        && "mcaquests:village_reputation"
                                .equals(reward.getAsJsonObject().get("type").getAsString())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Quest> bundledQuests() {
        List<Quest> quests = new ArrayList<>();
        try (Stream<Path> files = Files.walk(QUESTS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                quests.add(new Quest(path, JsonParser
                        .parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return quests;
    }

    /** The standing a quest of this band is worth when it authors none, straight off the config spec. */
    private static int defaultFor(String difficulty) {
        return switch (difficulty) {
            case "easy" -> McaQuestsConfig.COMMON.easyQuestReputation.getDefault();
            case "hard" -> McaQuestsConfig.COMMON.hardQuestReputation.getDefault();
            // "medium" and an undeclared band both land here, matching QuestDifficulty.DEFAULT.
            default -> McaQuestsConfig.COMMON.mediumQuestReputation.getDefault();
        };
    }

    @Test
    @DisplayName("the bundled pack is big enough for this check to mean anything")
    void thePackIsWhereItIsExpectedToBe() {
        assertTrue(bundledQuests().size() > 200,
                "found almost no bundled quests; the datapack has moved and this test is now vacuous");
    }

    /**
     * The regression. Every bundled quest must be worth <em>something</em> to the village that asked
     * for it, whether it says so itself or falls to the configured band default.
     */
    @Test
    @DisplayName("finishing any bundled quest changes the village's opinion of you")
    void everyBundledQuestIsWorthSomeStanding() {
        List<String> worthless = bundledQuests().stream()
                .filter(quest -> !quest.authorsReputation() && defaultFor(quest.difficulty()) == 0)
                .map(quest -> quest.id() + " (difficulty '" + quest.difficulty() + "')")
                .toList();

        assertTrue(worthless.isEmpty(), () -> worthless.size() + " bundled quest(s) award no village "
                + "standing at all: they declare neither a \"reputation\" block nor a "
                + "mcaquests:village_reputation reward, and the configured default for their difficulty "
                + "band is 0. Completing them cannot move the ladder they are measured against. First "
                + "few: " + worthless.stream().limit(5).toList());
    }

    /**
     * The class guard, one level up: it is not enough that a default exists, the pack must not gate
     * content behind standing it has no way to produce. Seven bundled quests do, and six of them are
     * the Townstead capstones that also pay it, so the pack gates on a threshold it mostly asks the
     * player to reach by other means - which makes a bundled source of standing a requirement for the
     * pack to be playable through, not a nicety.
     */
    @Test
    @DisplayName("standing the pack gates on is standing the pack can produce")
    void gatedContentIsReachable() {
        List<Quest> quests = bundledQuests();
        List<String> gated = quests.stream()
                .filter(quest -> {
                    String raw = quest.json().toString();
                    return raw.contains("mcaquests:reputation_tier")
                            || raw.contains("mcaquests:village_reputation\",\"min");
                })
                .map(Quest::id)
                .toList();
        assertFalse(gated.isEmpty(),
                "no bundled quest gates on standing any more; drop this test rather than weakening it");

        boolean questsProduceStanding = quests.stream()
                .anyMatch(quest -> quest.authorsReputation() || defaultFor(quest.difficulty()) > 0);
        assertTrue(questsProduceStanding, () -> gated.size() + " bundled quest(s) are gated on village "
                + "standing that no bundled quest can produce: " + gated.stream().limit(5).toList());
    }

    /** Projects always did author standing; this pins the asymmetry that made the gap easy to miss. */
    @Test
    @DisplayName("bundled projects still author their own standing, as they always have")
    void projectsStillAuthorStanding() throws IOException {
        try (Stream<Path> files = Files.walk(PROJECTS)) {
            long withReputation = files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("reputation");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .count();
            assertTrue(withReputation > 0, "bundled projects no longer award standing either");
        }
    }
}
