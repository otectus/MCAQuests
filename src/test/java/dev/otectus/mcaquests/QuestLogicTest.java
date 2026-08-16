package dev.otectus.mcaquests;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.data.ConditionRefs;
import dev.otectus.mcaquests.data.QuestChainValidator;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.WeightedPicker;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestAbandonedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestFailedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestNotCompletedCondition;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.QuestHistory;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

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
    void weightedPickerIsDeterministicAndDistinct() {
        List<String> items = List.of("a", "b", "c", "d", "e");
        ToIntFunction<String> weight = s -> 1;
        List<String> first = WeightedPicker.pickMany(items, weight, 42L, 3);
        assertEquals(first, WeightedPicker.pickMany(items, weight, 42L, 3), "same seed + order is stable");
        assertEquals(3, first.size());
        assertEquals(3, Set.copyOf(first).size(), "picks are distinct");
        assertEquals(items.size(), WeightedPicker.pickMany(items, weight, 1L, 99).size(), "count clamps to pool size");
        assertTrue(WeightedPicker.pickMany(List.of(), weight, 1L, 3).isEmpty(), "empty pool yields nothing");
    }

    @Test
    void weightedPickerFavorsHigherWeight() {
        List<String> items = List.of("light", "heavy");
        ToIntFunction<String> weight = s -> s.equals("heavy") ? 100 : 1;
        int heavyFirst = 0;
        for (int seed = 0; seed < 200; seed++) {
            if (WeightedPicker.pickMany(items, weight, seed, 1).get(0).equals("heavy")) {
                heavyFirst++;
            }
        }
        assertTrue(heavyFirst > 150, "the 100:1 weight should dominate the first pick (was " + heavyFirst + "/200)");
    }

    @Test
    void objectiveProgressNbtRoundTrip() {
        ObjectiveProgress progress = new ObjectiveProgress(7);
        progress.add(3);
        ObjectiveProgress restored = ObjectiveProgress.load(progress.save());
        assertEquals(10, restored.count(), "progress count survives an NBT save/load");
    }

    @Test
    void objectiveProgressNewFieldsRoundTrip() {
        ObjectiveProgress progress = new ObjectiveProgress(2);
        progress.addElapsed(140);
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000abc");
        progress.setTargetUuid(target);
        assertTrue(progress.addVisited(new BlockPos(1, 2, 3)), "first visit credits");
        assertFalse(progress.addVisited(new BlockPos(1, 2, 3)), "re-visiting the same pos never re-credits");
        assertTrue(progress.addVisited(new BlockPos(4, 5, 6)));
        progress.extra().putBoolean("seen_infected", true);

        ObjectiveProgress restored = ObjectiveProgress.load(progress.save());
        assertEquals(2, restored.count());
        assertEquals(140, restored.elapsedTicks(), "elapsed ticks survive save/load");
        assertEquals(target, restored.targetUuid(), "target uuid survives save/load");
        assertEquals(2, restored.visitedCount(), "visited set survives save/load");
        assertTrue(restored.hasVisited(new BlockPos(1, 2, 3)));
        assertTrue(restored.extra().getBoolean("seen_infected"), "extra scratch survives save/load");
    }

    @Test
    void objectiveProgressBackwardCompatibleWithCountOnlyTag() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("count", 5);
        ObjectiveProgress restored = ObjectiveProgress.load(legacy);
        assertEquals(5, restored.count(), "legacy count-only NBT still loads");
        assertEquals(0, restored.elapsedTicks());
        assertEquals(null, restored.targetUuid());
        assertEquals(0, restored.visitedCount());
    }

    @Test
    void villagerTargetCodecParsesAndValidates() {
        VillagerTarget family = parse(VillagerTarget.CODEC, "{\"mode\":\"family\",\"relation\":\"sibling\"}");
        assertEquals(VillagerTarget.Mode.FAMILY, family.mode());
        assertEquals(Optional.of("sibling"), family.relation());

        VillagerTarget self = parse(VillagerTarget.CODEC, "{\"mode\":\"self\"}");
        assertEquals(VillagerTarget.Mode.SELF, self.mode());

        assertTrue(VillagerTarget.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"mode\":\"bogus\"}"))
                .error().isPresent(), "an unknown mode fails to parse");

        // validate() flags a missing required field for the chosen mode.
        List<String> errors = new ArrayList<>();
        parse(VillagerTarget.CODEC, "{\"mode\":\"uuid\"}").validate("obj", errors);
        assertFalse(errors.isEmpty(), "uuid mode without a uuid is reported");
    }

    @Test
    void locationAnchorCodecParsesAndValidates() {
        LocationAnchor coords = parse(LocationAnchor.CODEC, "{\"anchor\":\"coords\",\"pos\":[1,2,3]}");
        assertEquals(LocationAnchor.Type.COORDS, coords.type());
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), coords.pos());

        LocationAnchor nearest = parse(LocationAnchor.CODEC, "{\"anchor\":\"nearest_village\",\"radius\":64}");
        assertEquals(LocationAnchor.Type.NEAREST_VILLAGE, nearest.type());
        assertEquals(Optional.of(64), nearest.radius());

        List<String> errors = new ArrayList<>();
        parse(LocationAnchor.CODEC, "{\"anchor\":\"coords\"}").validate("obj", errors);
        assertFalse(errors.isEmpty(), "coords anchor without a pos is reported");
    }

    // Note: StructureTarget's codec touches the STRUCTURE registry key, which transitively requires
    // MC bootstrap, so it cannot be exercised in these registry-free unit tests (validated in-game).

    private static <T> T parse(com.mojang.serialization.Codec<T> codec, String json) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.result().orElseThrow(() -> new AssertionError(
                "failed to parse '" + json + "': " + result.error().map(Object::toString).orElse("?")));
    }

    @Test
    void questHistoryNbtRoundTrip() {
        QuestHistory history = new QuestHistory();
        ResourceLocation quest = ResourceLocation.fromNamespaceAndPath("mcaquests", "farmer_wheat_request");
        UUID villager = UUID.randomUUID();
        history.recordCompletion(quest);
        history.recordCompletion(quest);
        history.setCooldownUntil(quest, villager, 5000L);

        QuestHistory restored = new QuestHistory();
        restored.load(history.save());
        assertEquals(2, restored.completionCount(quest), "completion count survives save/load");
        assertTrue(restored.onCooldown(quest, villager, 4000L), "still on cooldown before the deadline");
        assertFalse(restored.onCooldown(quest, villager, 6000L), "cooldown has expired after the deadline");
    }

    @Test
    void giverProfessionMatching() {
        ResourceLocation farmer = ResourceLocation.fromNamespaceAndPath("minecraft", "farmer");
        GiverSpec spec = new GiverSpec(List.of(farmer), true, -100, 1000);
        assertTrue(spec.acceptsProfession(farmer));
        assertFalse(spec.acceptsProfession(ResourceLocation.fromNamespaceAndPath("minecraft", "librarian")));
        assertTrue(GiverSpec.ANY.isGeneric());
        assertTrue(GiverSpec.ANY.acceptsProfession(farmer), "generic quests accept any profession");
    }

    @Test
    void giverHeartsBounds() {
        GiverSpec spec = new GiverSpec(List.of(), true, 50, 200);
        assertFalse(spec.acceptsHearts(49));
        assertTrue(spec.acceptsHearts(50));
        assertTrue(spec.acceptsHearts(200));
        assertFalse(spec.acceptsHearts(201));
    }

    @Test
    void cooldownAndCompletionHistory() {
        QuestHistory history = new QuestHistory();
        ResourceLocation quest = ResourceLocation.fromNamespaceAndPath("mcaquests", "farmer_wheat_request");
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
        ResourceLocation farmerMc = ResourceLocation.fromNamespaceAndPath("minecraft", "farmer");
        ResourceLocation farmerMca = ResourceLocation.fromNamespaceAndPath("mca", "farmer");
        ResourceLocation librarian = ResourceLocation.fromNamespaceAndPath("minecraft", "librarian");

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

    @Test
    void questHistoryOutcomesRoundTrip() {
        QuestHistory history = new QuestHistory();
        ResourceLocation quest = ResourceLocation.fromNamespaceAndPath("mcaquests", "farmer_help_apprentice");
        history.recordOutcome(quest, QuestHistory.Outcome.FAILED);
        history.recordOutcome(quest, QuestHistory.Outcome.FAILED);
        history.recordOutcome(quest, QuestHistory.Outcome.ABANDONED);
        history.recordOutcome(quest, QuestHistory.Outcome.COMPLETED); // routes to completion count

        QuestHistory restored = new QuestHistory();
        restored.load(history.save());
        assertEquals(2, restored.outcomeCount(quest, QuestHistory.Outcome.FAILED), "failures survive save/load");
        assertEquals(1, restored.outcomeCount(quest, QuestHistory.Outcome.ABANDONED), "abandons survive save/load");
        assertEquals(1, restored.outcomeCount(quest, QuestHistory.Outcome.COMPLETED), "completion shares the count map");
    }

    @Test
    void unlockCycleDetection() {
        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("mcaquests", "a");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("mcaquests", "b");
        ResourceLocation c = ResourceLocation.fromNamespaceAndPath("mcaquests", "c");

        // a -> b -> c is a clean linear chain: no cycle.
        assertTrue(QuestChainValidator.findUnlockCycle(
                Map.of(a, List.of(b), b, List.of(c), c, List.of())).isEmpty());

        // a -> b -> c -> a closes a loop: a cycle is reported.
        assertTrue(QuestChainValidator.findUnlockCycle(
                Map.of(a, List.of(b), b, List.of(c), c, List.of(a))).isPresent());

        // Self-unlock is the smallest cycle.
        assertTrue(QuestChainValidator.findUnlockCycle(Map.of(a, List.of(a))).isPresent());
    }

    @Test
    void chainValidatorRejectsPipeInChainId() {
        // '|' is reserved as the id|name separator in the FTB editor known-ids sync (task M5.1); a pipe
        // in a chain id would silently corrupt the flattened wire entry, so the validator rejects it.
        QuestDefinition good = chainQuest("good_quest", "the_family_farm");
        QuestDefinition bad = chainQuest("bad_quest", "the|family|farm");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        QuestChainValidator.validate(Map.of(good.id(), good), errors, warnings);
        assertTrue(errors.isEmpty(), () -> "pipe-free chain id must validate cleanly, got: " + errors);

        List<String> errors2 = new ArrayList<>();
        QuestChainValidator.validate(Map.of(bad.id(), bad), errors2, new ArrayList<>());
        assertTrue(errors2.stream().anyMatch(e -> e.contains("the|family|farm") && e.contains("'|'")),
                () -> "expected a pipe-rejection error naming the chain id, got: " + errors2);
    }

    /** A minimal enabled stage-1 quest whose only interesting feature is its {@code chain} id. */
    private static QuestDefinition chainQuest(String path, String chainId) {
        ChainSpec chain = new ChainSpec(chainId, 1, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), List.of());
        GiverSpec giver = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new QuestDefinition(ResourceLocation.fromNamespaceAndPath("mcaquests", path), true, 1, Optional.empty(),
                Optional.empty(), RepeatRule.DEFAULT, giver, Map.of(), List.of(), List.of(),
                TurnInSpec.DEFAULT, Optional.empty(), Optional.of(chain), Optional.empty(),
                Optional.empty(), OfferShaping.NONE, dev.otectus.mcaquests.quest.reputation.QuestReputationBlock.NONE);
    }

    @Test
    void perVillagerHistoryRoundTripAndIsolation() {
        QuestHistory history = new QuestHistory();
        ResourceLocation quest = ResourceLocation.fromNamespaceAndPath("mcaquests", "mapmaker_expedition_1_survey");
        UUID villagerA = UUID.randomUUID();
        UUID villagerB = UUID.randomUUID();
        history.recordCompletion(quest, villagerA);
        history.recordOutcome(quest, villagerA, QuestHistory.Outcome.FAILED);

        QuestHistory restored = new QuestHistory();
        restored.load(history.save());
        assertEquals(1, restored.completionCountByGiver(quest, villagerA), "per-giver completion survives save/load");
        assertEquals(0, restored.completionCountByGiver(quest, villagerB), "another villager shares no chain progress");
        assertEquals(1, restored.outcomeCountByGiver(quest, villagerA, QuestHistory.Outcome.FAILED),
                "per-giver failure survives save/load");
        assertEquals(0, restored.outcomeCountByGiver(quest, villagerB, QuestHistory.Outcome.FAILED),
                "another villager shares no failure record");
        assertEquals(1, restored.completionCount(quest), "global completion is still tracked alongside per-giver");
    }

    @Test
    void conditionRefsCollectsRequiredWithPolarity() {
        ResourceLocation x = ResourceLocation.fromNamespaceAndPath("mcaquests", "x");
        QuestCondition gate = new AllOfCondition(List.of(
                new QuestCompletedCondition(x, HistoryScope.GIVER),
                new QuestNotCompletedCondition(x, HistoryScope.GIVER)));
        List<ConditionRefs.Ref> required = ConditionRefs.required(Optional.of(gate));
        assertTrue(required.stream().anyMatch(r -> r.quest().equals(x) && r.polarity()), "completed(x) is required");
        assertTrue(required.stream().anyMatch(r -> r.quest().equals(x) && !r.polarity()), "not-completed(x) is required");
        QuestCondition anyOf = new AnyOfCondition(List.of(new QuestCompletedCondition(x, HistoryScope.GLOBAL)));
        assertTrue(ConditionRefs.required(Optional.of(anyOf)).isEmpty(), "any_of members are optional, not required");
    }

    @Test
    void conditionRefsDetectsOutcomeBranchAndReferences() {
        ResourceLocation y = ResourceLocation.fromNamespaceAndPath("mcaquests", "y");
        assertTrue(ConditionRefs.hasOutcomeBranch(Optional.of(new QuestFailedCondition(y, HistoryScope.GIVER))),
                "quest_failed marks an outcome branch");
        assertTrue(ConditionRefs.hasOutcomeBranch(Optional.of(new QuestAbandonedCondition(y, HistoryScope.GLOBAL))),
                "quest_abandoned marks an outcome branch");
        assertFalse(ConditionRefs.hasOutcomeBranch(Optional.of(new QuestCompletedCondition(y, HistoryScope.GIVER))),
                "plain quest_completed is not an outcome branch");
        assertTrue(ConditionRefs.allReferencedQuests(Optional.of(
                        new NotCondition(new QuestCompletedCondition(y, HistoryScope.GLOBAL)))).contains(y),
                "references are found through composites");
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
