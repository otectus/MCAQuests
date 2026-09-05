package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.BountifulBountiesObjective;
import dev.otectus.mcaquests.quest.objective.InteractBlockObjective;
import dev.otectus.mcaquests.quest.objective.KillEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.UseItemObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestPaths;
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
 * Parses the conditional Ice &amp; Fire and Bountiful packs through the real quest codec.
 *
 * <p>{@link BuiltinPackParsesTest} is rooted at the shipped datapack and never sees this content: a
 * compat pack lives under {@code compatpacks/} precisely because it is only mounted when the mod it
 * needs is installed. That is exactly why it needs its own parse test — nothing else in the build
 * would notice a quest here that stopped loading, and the failure would only appear on the
 * installations that have Ice &amp; Fire, which the CI machine does not.
 *
 * <p>The assertions are deliberately the tolerant ones. {@code iceandfire:fire_dragon} is not a
 * registered entity in a unit test, so what is checked is that the quest still <em>parses</em> and
 * that the objective reports itself unavailable — which is the whole design: a quest naming another
 * mod's content keeps its title and its progress and suspends, instead of vanishing at load.
 */
class CompatPackQuestParseTest {

    private static final Path PACK = TestPaths.of("src/main/resources/compatpacks/iafce_quests");

    /** The Bountiful pack, whose three quests are the only users of {@code bountiful_bounties}. */
    private static final Path BOUNTIFUL_PACK =
            TestPaths.of("src/main/resources/compatpacks/bountiful_core");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static List<Path> quests() {
        return quests(PACK);
    }

    private static List<Path> quests(Path pack) {
        try (Stream<Path> files = Files.walk(pack)) {
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
                    + result.error().map(DataResult.Error::message).orElse("?")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every quest in the Ice & Fire pack parses")
    void everyQuestParses() {
        List<Path> quests = quests();
        assertFalse(quests.isEmpty(), "the pack folder should hold quests; check the path");
        assertTrue(quests.size() == 8, "the pack ships eight quests; found " + quests.size());
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
        assertTrue(types.contains(UseItemObjective.class.getSimpleName()),
                "the seeker trial is the reason use_item exists; a codec mismatch here would have it "
                        + "silently fail to load");
        assertTrue(types.contains(KillEntityObjective.class.getSimpleName()),
                "the dragon and hydra hunts are kill_entity objectives naming another mod's entities");
    }

    @Test
    @DisplayName("content nothing has registered is unavailable, not a parse failure")
    void absentContentSuspendsRatherThanBreaking() {
        for (Path quest : quests()) {
            for (QuestObjective objective : parse(quest).objectives()) {
                if (objective instanceof UseItemObjective use) {
                    assertTrue(use.unofferableReason(null).isPresent(),
                            quest + " names an item this world does not have, so it must report "
                                    + "itself unavailable rather than being offered");
                    assertTrue(use.unavailableReason(null, null, new ObjectiveProgress(), null).isPresent());
                }
                if (objective instanceof KillEntityObjective kill && kill.target().entityId().isPresent()) {
                    assertTrue(kill.target().isUnresolved(),
                            quest + " names an entity this world does not have, so the target must "
                                    + "keep the written id rather than dropping it");
                    assertTrue(kill.unofferableReason(null).isPresent());
                }
            }
        }
    }

    @Test
    @DisplayName("every quest in the Bountiful pack parses, and uses the types it means to")
    void theBountifulPackParses() {
        List<Path> quests = quests(BOUNTIFUL_PACK);
        assertTrue(quests.size() == 3, "the pack ships three quests; found " + quests.size());

        List<String> types = new ArrayList<>();
        for (Path quest : quests) {
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
    @DisplayName("an absent board and an unhooked Bountiful suspend rather than breaking")
    void theBountifulPackSuspendsWithoutBountiful() {
        for (Path quest : quests(BOUNTIFUL_PACK)) {
            for (QuestObjective objective : parse(quest).objectives()) {
                if (objective instanceof InteractBlockObjective interact) {
                    assertTrue(interact.unofferableReason(null).isPresent(),
                            quest + " names a block this world does not have, so it must report "
                                    + "itself unavailable rather than being offered");
                }
                if (objective instanceof BountifulBountiesObjective bounties) {
                    assertTrue(bounties.unofferableReason(null).isPresent(),
                            quest + " cannot be counted without the cash-in hook, so it must report "
                                    + "itself unavailable rather than sitting at zero");
                    assertTrue(bounties.unavailableReason(null, null, new ObjectiveProgress(), null)
                            .isPresent());
                }
            }
        }
    }
}
