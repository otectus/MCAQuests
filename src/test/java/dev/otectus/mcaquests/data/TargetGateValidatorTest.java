package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.leaf.RelatedVillagerStatusCondition;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
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
 * The gate check, over quests built in code rather than read off disk.
 *
 * <p>{@code BuiltinFamilyGateTest} asserts the shipped pack is clean, which is the outcome that matters
 * but tells you nothing about <em>why</em> a file would be rejected. This pins the rules themselves,
 * including the two the validator must be careful <b>not</b> to enforce: an {@code any_of} branch does not
 * establish anything, and a giver may have one dead sibling and another alive.
 */
class TargetGateValidatorTest {

    private static final ResourceLocation ID = new ResourceLocation("testpack", "letter_to_brother");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestDefinition quest(VillagerTarget recipient, Optional<QuestCondition> conditions) {
        // The carried item is irrelevant here and left empty deliberately: this validator only ever
        // looks at the recipient, and naming a real item would pull the item registry into a test worker
        // whose registries were never populated.
        QuestObjective deliver = new DeliverToVillagerObjective(recipient,
                new ItemTarget(Optional.empty(), Optional.empty()), 1, true);
        GiverSpec giver = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new QuestDefinition(ID, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                giver, Map.of(), List.of(deliver), List.of(), TurnInSpec.DEFAULT, conditions,
                Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE,
                QuestReputationBlock.NONE);
    }

    private static VillagerTarget family(String relation, String require) {
        return new VillagerTarget(VillagerTarget.Mode.FAMILY, Optional.empty(), Optional.of(relation),
                Optional.empty(), Optional.ofNullable(require));
    }

    private static QuestCondition gate(String relation, String status) {
        return new RelatedVillagerStatusCondition(relation, status);
    }

    private static List<String> errorsFor(QuestDefinition def) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        quests.put(def.id(), def);
        List<String> errors = new ArrayList<>();
        TargetGateValidator.validate(quests, errors, new ArrayList<>());
        return errors;
    }

    private static List<String> warningsFor(QuestDefinition def) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        quests.put(def.id(), def);
        List<String> warnings = new ArrayList<>();
        TargetGateValidator.validate(quests, new ArrayList<>(), warnings);
        return warnings;
    }

    @Test
    @DisplayName("a family target with no conditions at all is rejected, naming the quest and relation")
    void ungatedFamilyTargetIsRejected() {
        List<String> errors = errorsFor(quest(family("sibling", null), Optional.empty()));

        assertEquals(1, errors.size(), () -> "expected exactly one finding, got " + errors);
        String message = errors.get(0);
        assertTrue(message.contains("testpack:letter_to_brother"), "the message must name the quest: " + message);
        assertTrue(message.contains("sibling"), "the message must name the relation: " + message);
        assertTrue(message.contains("mcaquests:related_villager_status"),
                "the message must show the block to add: " + message);
        assertTrue(message.contains("is_family_member"),
                "the message must say why the player-relative condition does not count: " + message);
    }

    @Test
    @DisplayName("the gate the reported bug's quest already had is accepted")
    void sameRelationGateIsAccepted() {
        assertEquals(List.of(),
                errorsFor(quest(family("sibling", "same_village"),
                        Optional.of(gate("sibling", "same_village")))));
    }

    @Test
    @DisplayName("a narrower gate establishes a broader target, but not the other way round")
    void relationCoverageIsOneDirectional() {
        // Proving a sibling exists proves a member of "any" exists.
        assertEquals(List.of(), errorsFor(quest(family("any", "same_village"),
                Optional.of(gate("sibling", "same_village")))));
        // Proving "some relative" exists proves nothing about a sibling in particular.
        assertEquals(1, errorsFor(quest(family("sibling", "same_village"),
                Optional.of(gate("any", "same_village")))).size());
        // "any" is the union of spouse/parent/child/sibling and deliberately excludes grandparent.
        assertEquals(1, errorsFor(quest(family("any", "same_village"),
                Optional.of(gate("grandparent", "same_village")))).size());
    }

    @Test
    @DisplayName("a target that deliberately names the dead or the missing needs no gate")
    void deliberateIntentNeedsNoGate() {
        assertEquals(List.of(), errorsFor(quest(family("child", "missing"), Optional.empty())));
        assertEquals(List.of(), errorsFor(quest(family("spouse", "dead"), Optional.empty())));
        assertEquals(List.of(), errorsFor(quest(family("any", "any_known"), Optional.empty())));
    }

    @Test
    @DisplayName("a gate whose status can never satisfy the require does not count as a gate")
    void disjointGateDoesNotEstablishExistence() {
        List<String> errors = errorsFor(quest(family("sibling", "same_village"),
                Optional.of(gate("sibling", "dead"))));
        assertEquals(1, errors.size(), () -> "expected the dead gate to establish nothing, got " + errors);
    }

    @Test
    @DisplayName("only a single-valued relation can be a hard contradiction")
    void contradictionIsLimitedToSingleValuedRelations() {
        // One spouse, required to be both dead and findable: impossible, and worth an error of its own.
        List<String> spouse = errorsFor(quest(family("spouse", "reachable"),
                Optional.of(gate("spouse", "dead"))));
        assertTrue(spouse.stream().anyMatch(e -> e.contains("only one spouse")),
                () -> "expected the impossible-pair message, got " + spouse);

        // widow_memorial's real shape: a dead spouse AND a living relative. Perfectly possible, and the
        // validator flagged it until the contradiction rule was narrowed to relations with one member.
        QuestCondition widow = new AllOfCondition(List.of(gate("spouse", "dead"),
                gate("any", "same_village")));
        assertEquals(List.of(), errorsFor(quest(family("any", "reachable"), Optional.of(widow))));
    }

    @Test
    @DisplayName("a gate inside an any_of establishes nothing, because it might not be the branch taken")
    void disjunctiveGateDoesNotCount() {
        QuestCondition either = new AnyOfCondition(List.of(gate("sibling", "same_village"),
                gate("child", "same_village")));
        assertEquals(1, errorsFor(quest(family("sibling", "same_village"), Optional.of(either))).size(),
                "an alternative is not a guarantee");
    }

    @Test
    @DisplayName("a gate nested inside an all_of is found")
    void conjunctiveNestingIsWalked() {
        QuestCondition nested = new AllOfCondition(List.of(
                new AllOfCondition(List.of(gate("sibling", "nearby")))));
        assertEquals(List.of(), errorsFor(quest(family("sibling", "nearby"), Optional.of(nested))));
    }

    @Test
    @DisplayName("targeting 'any' warns about the candidate order without refusing the quest")
    void anyRelationWarns() {
        QuestDefinition def = quest(family("any", "same_village"), Optional.of(gate("any", "same_village")));
        assertEquals(List.of(), errorsFor(def));
        List<String> warnings = warningsFor(def);
        assertEquals(1, warnings.size(), () -> "expected one advisory line, got " + warnings);
        assertTrue(warnings.get(0).contains("spouse"), "the warning must name the order: " + warnings.get(0));
    }

    @Test
    @DisplayName("a non-family target is not this validator's business")
    void nonFamilyTargetsAreIgnored() {
        assertEquals(List.of(), errorsFor(quest(VillagerTarget.SELF, Optional.empty())));
        assertFalse(VillagerTarget.SELF.requiresExistence());
    }
}
