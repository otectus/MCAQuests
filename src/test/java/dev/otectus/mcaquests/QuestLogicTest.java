package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

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
}
