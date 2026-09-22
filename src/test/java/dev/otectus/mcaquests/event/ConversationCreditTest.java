package dev.otectus.mcaquests.event;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.data.ObjectiveValidator;
import dev.otectus.mcaquests.data.QuestChainValidator;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.TalkToProfessionObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conversation credit (brief C01/C06, TALK-02/TALK-04) and the validators that must not mistake
 * intentionally excluded optional content for broken references (DEP-06).
 */
class ConversationCreditTest {

    static {
        TestBootstrap.ensureBootstrapped();
        dev.otectus.mcaquests.support.TestConfig.ensureCommonLoaded();
    }

    @AfterEach
    void reset() {
        ConversationCredit.clearSessionState();
    }

    @Test
    @DisplayName("TALK-02: one click reported by two routes in the same tick counts once")
    void sameInteractionFromTwoRoutesCountsOnce() {
        UUID player = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        assertTrue(ConversationCredit.firstReport(player, villager, 100L));
        assertFalse(ConversationCredit.firstReport(player, villager, 100L), "same tick, second route");
        assertFalse(ConversationCredit.firstReport(player, villager, 101L), "the event pair can straddle a tick");
        assertTrue(ConversationCredit.firstReport(player, villager, 140L), "a later conversation is a new report");
        assertTrue(ConversationCredit.firstReport(player, UUID.randomUUID(), 140L), "another villager is another conversation");
    }

    @Test
    @DisplayName("TALK-04: Missing Mile's conversation is tied to the destination village")
    void missingMileTalksAtTheDestination() throws Exception {
        QuestDefinition def = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/mcaquests/mcaquests/quests/chains/road_the_missing_mile.json"))))
                .result().orElseThrow();
        TalkToProfessionObjective talk = def.objectives().stream()
                .filter(TalkToProfessionObjective.class::isInstance).map(TalkToProfessionObjective.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Optional.of(0), talk.atLocationOf());
        assertTrue(TalkToProfessionObjective.anchorOf(def.objectives().get(0)).isPresent());

        List<String> errors = new ArrayList<>();
        ObjectiveValidator.validate(new java.util.HashMap<>(Map.of(def.id(), def)), errors, new ArrayList<>());
        assertEquals(List.of(), errors);
    }

    @Test
    @DisplayName("at_location_of must name a sibling with a location")
    void atLocationOfIsValidated() {
        QuestDefinition def = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"test:talk\",\"giver\":{},\"dialogue\":{},\"objectives\":["
                        + "{\"type\":\"mcaquests:kill_entity\",\"entity\":\"minecraft:zombie\",\"count\":1},"
                        + "{\"type\":\"mcaquests:talk_to_profession\",\"profession\":\"minecraft:cartographer\","
                        + "\"at_location_of\":0}]}")).result().orElseThrow();
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, QuestDefinition> loaded = new java.util.HashMap<>(Map.of(def.id(), def));
        ObjectiveValidator.validate(loaded, errors, new ArrayList<>());
        assertEquals(1, errors.stream().filter(e -> e.contains("at_location_of")).count(), errors.toString());
        assertTrue(loaded.isEmpty(), "a quest whose talk objective could never count anybody is not loaded");
    }

    @Test
    @DisplayName("DEP-06: a chain link into content excluded for a missing mod is not a dangling reference")
    void excludedChainTargetsAreNotDangling() {
        QuestDefinition source = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"test:first\",\"giver\":{},\"dialogue\":{},\"chain\":{\"chain\":\"c\",\"stage\":1,"
                        + "\"unlocks\":[\"test:second\"]}}")).result().orElseThrow();
        QuestDefinition target = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"test:second\",\"giver\":{},\"dialogue\":{},\"chain\":{\"chain\":\"c\",\"stage\":2,"
                        + "\"prerequisites\":[\"test:first\"]}}")).result().orElseThrow();
        List<String> withExclusion = new ArrayList<>();
        QuestChainValidator.validate(Map.of(source.id(), source), Map.of(target.id(), target), withExclusion, new ArrayList<>());
        assertEquals(List.of(), withExclusion);

        List<String> withoutExclusion = new ArrayList<>();
        QuestChainValidator.validate(Map.of(source.id(), source), withoutExclusion, new ArrayList<>());
        assertTrue(withoutExclusion.stream().anyMatch(e -> e.contains("unknown quest 'test:second'")),
                "a genuinely missing target is still reported: " + withoutExclusion);
        assertTrue(new ResourceLocation("test", "second").equals(target.id()));
    }
}
