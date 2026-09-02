package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.objective.DeliveryDestination;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObtainItemObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two datapack shapes that parse cleanly and can never behave as their author meant.
 *
 * <p>Both are legal JSON and both were accepted. Two possession objectives for one item are satisfied by
 * one stack, so "gather ten, then deliver ten" completes on ten; a {@code specified_profession} turn-in
 * with no professions names no villager in the world that can take the quest. In lenient mode — the
 * shipped default — the quest is logged and dropped rather than offered.
 *
 * <p>{@code BuiltinPackValidatesTest} covers the other half: no bundled quest trips either rule.
 */
class ObjectiveValidatorTest {

    private static final ResourceLocation ID = new ResourceLocation("testpack", "gather_and_deliver");

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    private static QuestDefinition quest(List<QuestObjective> objectives, TurnInSpec turnIn) {
        GiverSpec giver = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new QuestDefinition(ID, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                giver, Map.of(), objectives, List.of(), turnIn, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE,
                QuestReputationBlock.NONE);
    }

    private static ObtainItemObjective obtain(net.minecraft.world.item.Item item) {
        return new ObtainItemObjective(new ItemTarget(Optional.of(item), Optional.empty()), 10);
    }

    private static ItemDeliveryObjective deliver(net.minecraft.world.item.Item item) {
        return new ItemDeliveryObjective(item, 10, true, DeliveryDestination.CONSUMED);
    }

    /** Validates one quest, returning the errors; the map is mutated exactly as it is at load. */
    private static List<String> validate(Map<ResourceLocation, QuestDefinition> quests) {
        List<String> errors = new ArrayList<>();
        ObjectiveValidator.validate(quests, errors);
        return errors;
    }

    private static Map<ResourceLocation, QuestDefinition> loaded(QuestDefinition def) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        quests.put(def.id(), def);
        return quests;
    }

    @Test
    @DisplayName("an obtain and a delivery of the same item are rejected and the quest is skipped")
    void obtainAndDeliverOneItem() {
        Map<ResourceLocation, QuestDefinition> quests =
                loaded(quest(List.of(obtain(Items.WHEAT), deliver(Items.WHEAT)), TurnInSpec.DEFAULT));

        List<String> errors = validate(quests);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("two possession objectives"), errors.get(0));
        assertTrue(quests.isEmpty(), "a quest that completes on one stack must not reach the offer pool");
    }

    @Test
    @DisplayName("two obtains of the same item are rejected")
    void twoObtainsOfOneItem() {
        Map<ResourceLocation, QuestDefinition> quests =
                loaded(quest(List.of(obtain(Items.WHEAT), obtain(Items.WHEAT)), TurnInSpec.DEFAULT));

        assertEquals(1, validate(quests).size());
        assertTrue(quests.isEmpty());
    }

    @Test
    @DisplayName("possession objectives for different items are fine")
    void differentItemsAreFine() {
        Map<ResourceLocation, QuestDefinition> quests =
                loaded(quest(List.of(obtain(Items.WHEAT), deliver(Items.BREAD)), TurnInSpec.DEFAULT));

        assertTrue(validate(quests).isEmpty());
        assertFalse(quests.isEmpty());
    }

    @Test
    @DisplayName("specified_profession with no professions is rejected and the quest is skipped")
    void specifiedProfessionWithNoProfessions() {
        Map<ResourceLocation, QuestDefinition> quests = loaded(quest(List.of(obtain(Items.WHEAT)),
                new TurnInSpec(TurnInMode.SPECIFIED_PROFESSION, List.of())));

        List<String> errors = validate(quests);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("specified_profession"), errors.get(0));
        assertTrue(quests.isEmpty());
    }

    @Test
    @DisplayName("specified_profession that names a profession is fine")
    void specifiedProfessionWithAProfession() {
        Map<ResourceLocation, QuestDefinition> quests = loaded(quest(List.of(obtain(Items.WHEAT)),
                new TurnInSpec(TurnInMode.SPECIFIED_PROFESSION,
                        List.of(new ResourceLocation("minecraft", "farmer")))));

        assertTrue(validate(quests).isEmpty());
        assertFalse(quests.isEmpty());
    }
}
