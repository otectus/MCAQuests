package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A quest with no {@code title} renders the key its id derives, so a missing one is reported (1.7.1) —
 * it is what put "Quest complete: mcaquests.quest.rt_deliver.title" in a player's chat.
 */
class MissingTitleKeyWarningTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private Language previous;

    @BeforeEach
    void injectLanguage() {
        previous = Language.getInstance();
        Language.inject(language(Set.of("mcaquests.status.no_quests", "mcaquests.quest.named.title")));
    }

    @AfterEach
    void restoreLanguage() {
        Language.inject(previous);
    }

    private static QuestDefinition quest(String path, Optional<QuestText> title) {
        return new QuestDefinition(ResourceLocation.fromNamespaceAndPath("testpack", path), true, 1, Optional.empty(), title,
                RepeatRule.DEFAULT, new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE), Map.of(),
                List.of(new ItemDeliveryObjective(Items.BREAD, 1, true)), List.of(), TurnInSpec.DEFAULT,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE,
                QuestReputationBlock.NONE);
    }

    @Test
    @DisplayName("an untitled quest whose derived key is undefined is reported; the other shapes are not")
    void untitledQuestWithUndefinedKey() {
        List<String> warnings = TranslationKeyValidator.collectWarnings(List.of(
                quest("untitled", Optional.empty()),
                quest("named", Optional.empty()),
                quest("inline", Optional.of(new QuestText(Optional.of("Bread for Anna"), Optional.empty(), List.of())))),
                List.of());

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("testpack:untitled")
                && warnings.get(0).contains("mcaquests.quest.untitled.title"), warnings.get(0));
    }

    /** A language that defines exactly the given keys. */
    private static Language language(Set<String> keys) {
        return new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return keys.contains(key) ? key : fallback;
            }

            @Override
            public boolean has(String key) {
                return keys.contains(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        };
    }
}
