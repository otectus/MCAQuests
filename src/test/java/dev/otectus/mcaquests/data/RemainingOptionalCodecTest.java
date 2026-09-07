package dev.otectus.mcaquests.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.dialogue.VoiceLine;
import dev.otectus.mcaquests.quest.dialogue.VoicePool;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.situation.trigger.InfectionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.LowFoodTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.MissingKinTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.NightTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
import dev.otectus.mcaquests.quest.title.TitleDefinition;
import dev.otectus.mcaquests.quest.title.TitleScope;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemainingOptionalCodecTest {
    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    @Test
    void malformedDialogueConditionsNeverBecomeUnconditionalFallbacks() {
        assertRejected(VoiceLine.CODEC, "{\"text\":\"Only after an achievement\",\"when\":{\"type\":\"missingmod:unknown\"}}");
        assertRejected(VoiceLine.CODEC, "{\"text\":\"Only with enough hearts\",\"when\":{\"type\":\"mcaquests:hearts\",\"min\":\"typo\"}}");
        assertRejected(VoicePool.CODEC, "{\"state\":\"no_quests\",\"lines\":[{\"text\":\"Gated\",\"when\":{\"type\":\"missingmod:unknown\"}}]}");
        assertRejected(VoiceLine.CODEC, "{\"text\":\"Invalid weight\",\"weight\":0}");
        assertRejected(VoicePool.CODEC, "{\"state\":\"no_quests\",\"lines\":[{\"text\":\"Fallback\"}],\"priority\":\"typo\"}");

        VoiceLine fallback = parse(VoiceLine.CODEC, "{\"text\":\"Fallback\"}");
        assertTrue(fallback.isFallback());
        assertTrue(fallback.matches(null));
        assertEquals(1, fallback.weight());
        assertEquals(0, parse(VoicePool.CODEC,
                "{\"state\":\"no_quests\",\"lines\":[{\"text\":\"Fallback\"}]}").priority());
        assertFalse(parse(VoiceLine.CODEC,
                "{\"text\":\"Gated\",\"when\":{\"type\":\"mcaquests:hearts\",\"min\":5}}").isFallback());
    }

    @Test
    void textFieldValidationPreservesEstablishedEmptyAndTranslationPrecedence() {
        assertRejected(QuestText.CODEC, "{\"text\":[]}");
        assertRejected(QuestText.CODEC, "{\"translate\":{}}");
        assertRejected(QuestText.CODEC, "{\"translate\":\"test.key\",\"with\":{}}");
        assertEquals("", parse(QuestText.CODEC, "{}").resolve().getString());
        assertEquals("Literal", parse(QuestText.CODEC, "{\"text\":\"Literal\"}").resolve().getString());
        QuestText both = parse(QuestText.CODEC, "{\"text\":\"Literal\",\"translate\":\"test.key\"}");
        assertInstanceOf(TranslatableContents.class, both.resolve().getContents());
        assertEquals("test.key", ((TranslatableContents) both.resolve().getContents()).getKey());
        assertEquals(List.of("{count}"), parse(QuestText.CODEC,
                "{\"translate\":\"test.key\",\"with\":[\"{count}\"]}").with());
    }

    @Test
    void malformedTitleScopeAndTierGrantAreRejected() {
        assertRejected(TitleDefinition.CODEC, "{\"name\":\"Champion\",\"scope\":\"globla\"}");
        assertRejected(ReputationTier.CODEC,
                "{\"id\":\"friend\",\"threshold\":10,\"name\":\"Friend\",\"grants_title\":{}}");
        assertEquals(TitleScope.VILLAGE, parse(TitleDefinition.CODEC, "{\"name\":\"Champion\"}").scope());
        assertEquals(TitleScope.GLOBAL, parse(TitleDefinition.CODEC,
                "{\"name\":\"Champion\",\"scope\":\"global\"}").scope());
        assertTrue(parse(ReputationTier.CODEC,
                "{\"id\":\"friend\",\"threshold\":10,\"name\":\"Friend\"}").grantsTitle().isEmpty());
    }

    @Test
    void malformedTriggerFiltersDoNotBroadenSignals() {
        assertRejected(LowFoodTrigger.CODEC, "{\"threshold\":\"typo\"}");
        assertRejected(VillagerDeathTrigger.CODEC, "{\"relation\":[]}");
        assertRejected(MissingKinTrigger.CODEC, "{\"relation\":{}}");
        assertRejected(NightTrigger.CODEC, "{\"require_full_moon\":\"typo\"}");
        assertRejected(InfectionTrigger.CODEC, "{\"min_progress\":2.0}");
        assertEquals(16, parse(LowFoodTrigger.CODEC, "{}").threshold());
        assertEquals("any", parse(VillagerDeathTrigger.CODEC, "{}").relation());
        assertEquals("any", parse(MissingKinTrigger.CODEC, "{}").relation());
        assertFalse(parse(NightTrigger.CODEC, "{}").requireFullMoon());
        assertEquals(0.0f, parse(InfectionTrigger.CODEC, "{}").minProgress());
    }

    private static void assertRejected(Codec<?> codec, String json) {
        var result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.error().isPresent(), json);
        assertTrue(result.result().isEmpty(), json);
    }

    private static <T> T parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }
}
