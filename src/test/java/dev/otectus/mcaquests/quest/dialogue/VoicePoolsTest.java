package dev.otectus.mcaquests.quest.dialogue;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that decide what a villager says.
 *
 * <p>Three of them matter enough to be checked rather than believed:
 *
 * <ul>
 *   <li><b>A conditioned line beats a fallback.</b> Otherwise a pool that says something specific
 *       about a grumpy villager has that specificity averaged away by the general lines beside it,
 *       and the feature reads as broken rather than as subtle.</li>
 *   <li><b>A fallback is always there to catch.</b> Every caller has its own fallback, so an empty
 *       answer is safe — but a pool that has an unconditioned line and returns nothing anyway would
 *       silently waste content the pack author wrote.</li>
 *   <li><b>Shadowing means shadowing.</b> A higher-priority pool that has something to say ends the
 *       search, rather than being pooled with what it was meant to replace.</li>
 * </ul>
 *
 * <p>Selection takes a predicate rather than a {@link QuestContext}, so none of this needs a server —
 * the same reason {@code ScrollView} and {@code PanelGeometry} are shaped the way they are.
 */
class VoicePoolsTest {

    static {
        TestBootstrap.ensureBootstrapped();
        // Touch a codec that pulls the registries up in the order the loader does; initialising a
        // *Types class first reaches Registries before the bootstrap flag has had any effect. Same
        // reason, and same fix, as DispatchedCodecInlinesTest.
        assertTrue(QuestDefinition.CODEC != null);
    }

    /** A condition that is never evaluated here: the predicate under test stands in for it. */
    private static final QuestCondition MARKER = new QuestCondition() {
        @Nullable
        @Override
        public QuestConditionType<?> type() {
            return null;
        }

        @Override
        public boolean test(QuestContext context) {
            throw new AssertionError("selection must go through the predicate, not the condition");
        }
    };

    private static VoiceLine conditioned(String text, int weight) {
        return new VoiceLine(Optional.of(MARKER), QuestText.literal(text), weight);
    }

    private static VoiceLine fallback(String text) {
        return new VoiceLine(Optional.empty(), QuestText.literal(text), 1);
    }

    private static VoicePool pool(int priority, VoiceLine... lines) {
        return new VoicePool(VoicePool.GREETING, priority, List.of(lines));
    }

    private static Predicate<VoiceLine> matching(String... texts) {
        List<String> wanted = List.of(texts);
        return line -> wanted.contains(line.text().text().orElse(""));
    }

    @Test
    @DisplayName("a matching conditioned line is preferred over the fallbacks beside it")
    void conditionedBeatsFallback() {
        VoicePool pool = pool(0, conditioned("grumpy", 1), fallback("anyone"));

        List<VoiceLine> eligible = VoicePools.eligible(List.of(pool), matching("grumpy"));

        assertEquals(1, eligible.size());
        assertEquals("grumpy", eligible.get(0).text().text().orElseThrow());
    }

    @Test
    @DisplayName("when no conditioned line matches, the fallbacks are what is left")
    void fallbackCatchesEverythingElse() {
        VoicePool pool = pool(0, conditioned("grumpy", 1), fallback("anyone"));

        List<VoiceLine> eligible = VoicePools.eligible(List.of(pool), matching("nothing"));

        assertEquals(1, eligible.size());
        assertEquals("anyone", eligible.get(0).text().text().orElseThrow());
    }

    @Test
    @DisplayName("a pool of only unmatched conditioned lines leaves the villager to their caller's fallback")
    void noFallbackMeansNoLine() {
        VoicePool pool = pool(0, conditioned("grumpy", 1));

        assertTrue(VoicePools.eligible(List.of(pool), matching("nothing")).isEmpty(),
                "empty is a safe answer: every caller has a static line of its own to fall back to");
    }

    @Test
    @DisplayName("a higher-priority pool shadows a lower one rather than being pooled with it")
    void higherPriorityShadows() {
        VoicePool override = pool(10, conditioned("from the pack", 1));
        VoicePool builtin = pool(0, conditioned("from the mod", 1), fallback("floor"));

        // poolsFor sorts by priority; eligible() receives them already ordered.
        List<VoiceLine> eligible = VoicePools.eligible(List.of(override, builtin),
                matching("from the pack", "from the mod"));

        assertEquals(1, eligible.size(), "the built-in line must not be averaged in with the override");
        assertEquals("from the pack", eligible.get(0).text().text().orElseThrow());
    }

    @Test
    @DisplayName("a lower-priority pool still contributes when the higher one has nothing to say")
    void lowerPriorityStillCounts() {
        VoicePool override = pool(10, conditioned("only for night owls", 1));
        VoicePool builtin = pool(0, conditioned("from the mod", 1));

        List<VoiceLine> eligible = VoicePools.eligible(List.of(override, builtin), matching("from the mod"));

        assertEquals(1, eligible.size());
        assertEquals("from the mod", eligible.get(0).text().text().orElseThrow());
    }

    @Test
    @DisplayName("the same seed always chooses the same line")
    void selectionIsDeterministic() {
        // This is the 1.4.3 guarantee one layer down: reopening a villager's menu must not re-voice
        // them. A pool that rolled afresh per render would reintroduce the reported bug exactly.
        List<VoiceLine> candidates = List.of(fallback("a"), fallback("b"), fallback("c"));

        VoiceLine first = VoicePools.weightedPick(candidates, new Random(1234L));
        VoiceLine again = VoicePools.weightedPick(candidates, new Random(1234L));

        assertSame(first, again);
    }

    /**
     * Well-spread seeds.
     *
     * <p>Sampling with 0, 1, 2, ... is a trap: {@code new Random(n).nextInt(2)} returns 1 for every
     * small sequential {@code n}, because Java's generator scrambles the seed too little before the
     * first draw. Production never sees sequential seeds — {@code QuestContext.stableRandom} mixes
     * player, villager, day and quest hashes — so this spreads them the same way rather than
     * measuring an artefact of the test.
     */
    private static Random seeded(int index) {
        return new Random(index * 0x9E3779B97F4A7C15L);
    }

    @Test
    @DisplayName("weight shifts the odds, and every line stays reachable")
    void weightIsRespected() {
        VoiceLine heavy = conditioned("heavy", 9);
        VoiceLine light = conditioned("light", 1);
        List<VoiceLine> candidates = List.of(heavy, light);

        int heavyCount = 0;
        boolean sawLight = false;
        for (int seed = 0; seed < 400; seed++) {
            VoiceLine picked = VoicePools.weightedPick(candidates, seeded(seed));
            if (picked == heavy) {
                heavyCount++;
            } else {
                sawLight = true;
            }
        }
        assertTrue(heavyCount > 300, "a 9:1 weight should land heavy far more often; got " + heavyCount);
        assertTrue(sawLight, "the light line must still be reachable, or its weight is a lie");
    }

    @Test
    @DisplayName("a zero or negative weight is treated as one rather than making a line unreachable")
    void degenerateWeightsCannotStarveALine() {
        // The codec forbids these, but a pool can also be constructed in code by an add-on, and a
        // line nobody can ever hear is worse than a line that is merely rare.
        VoiceLine zero = new VoiceLine(Optional.empty(), QuestText.literal("zero"), 0);
        VoiceLine normal = fallback("normal");

        boolean sawZero = false;
        for (int seed = 0; seed < 200 && !sawZero; seed++) {
            sawZero = VoicePools.weightedPick(List.of(zero, normal), seeded(seed)) == zero;
        }
        assertTrue(sawZero, "a zero-weight line should still be reachable");
        assertFalse(VoicePools.eligible(List.of(pool(0, zero)), matching()).isEmpty());
    }
}
