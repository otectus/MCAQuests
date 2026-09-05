package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.BountifulBountiesObjective;
import dev.otectus.mcaquests.quest.objective.InteractBlockObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the conditional Bountiful pack through the real quest codec.
 *
 * <p>{@link BuiltinPackParsesTest} is rooted at the shipped datapack and never sees this content: a
 * compat pack lives under {@code compatpacks/} precisely because it is only mounted when the mod it
 * needs is installed. That is exactly why it needs its own parse test — nothing else in the build
 * would notice a quest here that stopped loading, and the failure would only appear on the
 * installations that have Bountiful, which the CI machine does not.
 *
 * <p>The assertions are deliberately the tolerant ones. {@code bountiful:bountyboard} is not a
 * registered block in a unit test, so what is checked is that the quest still <em>parses</em> and
 * that the objective reports itself unavailable — which is the whole design: a quest naming another
 * mod's block keeps its title and its progress and suspends, instead of vanishing at load.
 */
class CompatPackQuestParseTest {

    private static final Path PACK = Path.of("src/main/resources/compatpacks/bountiful_core");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static List<Path> quests() {
        try (Stream<Path> files = Files.walk(PACK)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/quests/"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static QuestDefinition parse(Path file) {
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            DataResult<QuestDefinition> result = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            return result.result().orElseThrow(() -> new AssertionError(file + " did not parse: "
                    + result.error().map(DataResult.PartialResult::message).orElse("?")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every quest in the Bountiful pack parses")
    void everyQuestParses() {
        List<Path> quests = quests();
        assertFalse(quests.isEmpty(), "the pack folder should hold quests; check the path");
        for (Path quest : quests) {
            parse(quest);
        }
    }

    @Test
    @DisplayName("the pack's objectives really are the types it means to use")
    void theObjectivesAreTheOnesTheyClaim() {
        List<String> types = new ArrayList<>();
        for (Path quest : quests()) {
            for (QuestObjective objective : parse(quest).objectives()) {
                types.add(objective.getClass().getSimpleName());
            }
        }
        assertTrue(types.contains(InteractBlockObjective.class.getSimpleName()),
                "the board-discovery quest is the reason interact_block exists");
        assertTrue(types.contains(BountifulBountiesObjective.class.getSimpleName()),
                "the contractor and specialist quests are the reason bountiful_bounties exists; a "
                        + "codec mismatch here would have them silently fail to load");
    }

    @Test
    @DisplayName("a board nothing has registered is unavailable, not a parse failure")
    void anAbsentBoardSuspendsRatherThanBreaking() {
        for (Path quest : quests()) {
            for (QuestObjective objective : parse(quest).objectives()) {
                if (objective instanceof InteractBlockObjective interact) {
                    assertTrue(interact.unofferableReason(null).isPresent(),
                            quest + " names a block this world does not have, so it must report "
                                    + "itself unavailable rather than being offered");
                }
            }
        }
    }
}
