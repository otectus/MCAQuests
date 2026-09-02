package dev.otectus.mcaquests.state;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * An active quest's progress list is sized once, when the quest is accepted, but every reader indexes it
 * by the live definition. A quest that gains an objective — {@code lost_child_2_deeper} gained an escort
 * in 1.5.0, and any pack author can do the same under {@code /reload} — would otherwise throw
 * {@code IndexOutOfBoundsException} on login and on every progress tick for anyone still holding it.
 */
class ActiveQuestReconcileTest {

    private static final Path DEEPER = Path.of(
            "src/main/resources/data/mcaquests/mcaquests/quests/chains/lost_child/2_deeper.json");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("progress pads to a higher index and the extra entry round-trips")
    void progressPadsAndPersists() {
        ActiveQuest quest = ActiveQuest.create(
                new ResourceLocation("mcaquests", "test_quest"),
                UUID.randomUUID(),
                Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"),
                new ResourceLocation("minecraft", "overworld"),
                0L,
                1,
                null);

        ObjectiveProgress added = quest.progress(1);
        assertAll(
                () -> assertNotNull(added, "a missing objective must get a fresh progress entry"),
                () -> assertEquals(0, added.count(), "the new objective starts at 0"));

        CompoundTag saved = quest.save();
        ActiveQuest reloaded = ActiveQuest.load(saved);
        assertEquals(2, saved.getList("progress", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                "save() writes whatever the list now holds");
        assertEquals(0, reloaded.progress(1).count(), "and load() reads both entries back");
    }

    @Test
    @DisplayName("a 1.4.x lost_child_2_deeper does not throw against the 1.5.0 definition")
    void heldQuestSurvivesAnAddedObjective() {
        QuestDefinition def = parse(DEEPER);
        assertEquals(2, def.objectives().size(),
                "this test is only meaningful while the shipped stage has more than one objective");

        // The shape a 1.4.x save has: accepted when the stage had a single objective.
        List<ObjectiveProgress> progress = new ArrayList<>();
        progress.add(new ObjectiveProgress());
        ActiveQuest held = new ActiveQuest(
                def.id(),
                UUID.randomUUID(),
                Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"),
                new ResourceLocation("minecraft", "overworld"),
                0L,
                progress,
                null,
                null);

        assertDoesNotThrow(() -> {
            held.resolve(def);
            for (int i = 0; i < def.objectives().size(); i++) {
                held.progress(i);
            }
        }, "logging in with a held quest whose definition gained an objective must not throw");
    }

    private static QuestDefinition parse(Path file) {
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        DataResult<QuestDefinition> result =
                QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(raw));
        return result.result().orElseThrow(() -> new AssertionError(
                file + " did not parse: " + result.error().map(DataResult.PartialResult::message).orElse("?")));
    }
}
