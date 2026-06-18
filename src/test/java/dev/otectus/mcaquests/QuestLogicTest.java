package dev.otectus.mcaquests;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for the quest engine (no game launch / MCA needed) — spec section 33. */
class QuestLogicTest {

    @Test
    void repeatRuleDefaults() {
        assertEquals(RepeatRule.RepeatType.COOLDOWN, RepeatRule.DEFAULT.type());
        assertTrue(RepeatRule.DEFAULT.isRepeatable());
        assertFalse(new RepeatRule(RepeatRule.RepeatType.ONCE, 0).isRepeatable());
    }

    @Test
    void giverProfessionMatching() {
        ResourceLocation farmer = new ResourceLocation("minecraft", "farmer");
        GiverSpec spec = new GiverSpec(List.of(farmer), true, -100, 1000);
        assertTrue(spec.acceptsProfession(farmer));
        assertFalse(spec.acceptsProfession(new ResourceLocation("minecraft", "librarian")));
        assertTrue(GiverSpec.ANY.isGeneric());
        assertTrue(GiverSpec.ANY.acceptsProfession(farmer), "generic quests accept any profession");
    }

    @Test
    void giverFavorBounds() {
        GiverSpec spec = new GiverSpec(List.of(), true, 50, 200);
        assertFalse(spec.acceptsFavor(49));
        assertTrue(spec.acceptsFavor(50));
        assertTrue(spec.acceptsFavor(200));
        assertFalse(spec.acceptsFavor(201));
    }

    @Test
    void cooldownAndCompletionHistory() {
        QuestHistory history = new QuestHistory();
        ResourceLocation quest = new ResourceLocation("mcaquests", "farmer_wheat_request");
        UUID villager = UUID.randomUUID();

        assertFalse(history.onCooldown(quest, villager, 0L));
        history.setCooldownUntil(quest, villager, 24000L);
        assertTrue(history.onCooldown(quest, villager, 100L));
        assertFalse(history.onCooldown(quest, villager, 24000L), "available once the cooldown game-time is reached");
        // A different villager shares no cooldown.
        assertFalse(history.onCooldown(quest, UUID.randomUUID(), 100L));

        assertEquals(0, history.completionCount(quest));
        history.recordCompletion(quest);
        history.recordCompletion(quest);
        assertEquals(2, history.completionCount(quest));
    }

    @Test
    void professionMatchingModes() {
        ResourceLocation farmerMc = new ResourceLocation("minecraft", "farmer");
        ResourceLocation farmerMca = new ResourceLocation("mca", "farmer");
        ResourceLocation librarian = new ResourceLocation("minecraft", "librarian");

        assertTrue(ProfessionMatcher.matches(farmerMc, farmerMc, ProfessionMatchingMode.STRICT));
        assertFalse(ProfessionMatcher.matches(farmerMc, farmerMca, ProfessionMatchingMode.STRICT));

        assertTrue(ProfessionMatcher.matches(farmerMc, farmerMca, ProfessionMatchingMode.NORMALIZED),
                "same path, different namespace matches under NORMALIZED");
        assertFalse(ProfessionMatcher.matches(farmerMc, librarian, ProfessionMatchingMode.NORMALIZED));

        assertTrue(ProfessionMatcher.matchesAny(List.of(farmerMc), farmerMca, ProfessionMatchingMode.NORMALIZED));
        assertFalse(ProfessionMatcher.matchesAny(List.of(farmerMc), null, ProfessionMatchingMode.NORMALIZED));
    }

    @Test
    void conditionComposites() {
        QuestCondition t = fixed(true);
        QuestCondition f = fixed(false);

        assertTrue(new AllOfCondition(List.of(t, t)).test(null));
        assertFalse(new AllOfCondition(List.of(t, f)).test(null));
        assertTrue(new AnyOfCondition(List.of(f, t)).test(null));
        assertFalse(new AnyOfCondition(List.of(f, f)).test(null));
        assertTrue(new NotCondition(f).test(null));
        assertFalse(new NotCondition(t).test(null));
    }

    /** A leaf condition that ignores context and returns a fixed value (for composite tests). */
    private static QuestCondition fixed(boolean value) {
        return new QuestCondition() {
            @Nullable
            @Override
            public QuestConditionType<?> type() {
                return null;
            }

            @Override
            public boolean test(QuestContext context) {
                return value;
            }
        };
    }
}
