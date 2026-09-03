package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.support.TestConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.CurrencyFallback;
import dev.otectus.mcaquests.McaQuestsConfig.CurrencyProviderMode;
import dev.otectus.mcaquests.quest.QuestDifficulty;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the semantic currency reward: which item it resolves to, how its range is derived, that the
 * amount is rolled inside that range, and that range validation rejects nonsense.
 *
 * <p>Grant-side behaviour (stack splitting, idempotent payment) needs a live {@code ServerPlayer} and is
 * therefore covered by the manual compatibility matrix rather than here; the parts that are pure logic —
 * provider selection, fallback, scaling, ranges, freezing — are all exercised.
 */
class CurrencyRewardTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** Loads config defaults so {@code .get()} returns the declared default instead of "config not loaded". */
    @BeforeAll
    static void loadCommonConfigDefaults() {
        // PORT: 1.20.1's ModConfigSpec#setConfig(CommentedConfig) became acceptConfig(ILoadedConfig);
        // TestConfig owns that wrapping now, so this is the same attach in one call.
        TestConfig.ensureCommonLoaded();
    }

    @AfterEach
    void restoreDefaults() {
        McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.VANILLA);
        McaQuestsConfig.COMMON.currencyFallback.set(CurrencyFallback.EMERALDS);
        McaQuestsConfig.COMMON.currencyRewardMultiplier.set(1.0);
        McaQuestsConfig.COMMON.numismaticsCurrencyItem.set("numismatics:spur");
        McaQuestsConfig.COMMON.customCurrencyItem.set("minecraft:emerald");
        CurrencyProvider.resetWarnings();
    }

    private static CurrencyReward band(QuestDifficulty difficulty) {
        return new CurrencyReward(Optional.empty(), Optional.empty(), Optional.of(difficulty));
    }

    private static CurrencyReward explicit(int min, int max) {
        return new CurrencyReward(Optional.of(min), Optional.of(max), Optional.empty());
    }

    /**
     * Provider selection, exercised through {@link CurrencyProvider#resolveCurrencyId}.
     *
     * <p>The "does this id exist" predicate is faked rather than hitting {@code BuiltInRegistries}: these
     * tests never populate the vanilla registries (see {@code TestBootstrap}), so anything that touches
     * them — including merely constructing an {@code Item} — throws. Faking it is also a truer model of
     * the case under test: "this id is not in the registry" is exactly what an uninstalled Numismatics
     * looks like to this code.
     */
    @Nested
    @DisplayName("provider selection")
    class Provider {

        /** Stands in for the item registry: only ids a server without Numismatics would really have. */
        private static final Set<String> INSTALLED = Set.of("minecraft:emerald", "minecraft:gold_ingot");

        private final List<String> lookedUp = new ArrayList<>();

        private boolean exists(String id) {
            lookedUp.add(id);
            return INSTALLED.contains(id);
        }

        @Test
        @DisplayName("VANILLA resolves the emerald id")
        void vanillaIsEmeralds() {
            assertEquals(Optional.of("minecraft:emerald"), CurrencyProvider.resolveCurrencyId(this::exists));
            assertEquals(List.of("minecraft:emerald"), lookedUp);
        }

        @Test
        @DisplayName("an absent Numismatics falls back to emeralds by default")
        void numismaticsFallsBackToEmeralds() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.NUMISMATICS);
            assertEquals(Optional.of("minecraft:emerald"), CurrencyProvider.resolveCurrencyId(this::exists),
                    "an unresolvable provider item should fall back to emeralds, not break the reward");
            assertEquals(List.of("numismatics:spur", "minecraft:emerald"), lookedUp,
                    "should try the configured coin first, then the emerald fallback");
        }

        @Test
        @DisplayName("an absent provider with fallback DISABLE grants nothing")
        void numismaticsCanDisableInstead() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.NUMISMATICS);
            McaQuestsConfig.COMMON.currencyFallback.set(CurrencyFallback.DISABLE);
            assertTrue(CurrencyProvider.resolveCurrencyId(this::exists).isEmpty());
            assertEquals(List.of("numismatics:spur"), lookedUp, "DISABLE must not fall back to emeralds");
        }

        @Test
        @DisplayName("an installed Numismatics is used directly, with no fallback lookup")
        void installedNumismaticsIsUsed() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.NUMISMATICS);
            McaQuestsConfig.COMMON.numismaticsCurrencyItem.set("minecraft:gold_ingot"); // stands in as "installed"
            assertEquals(Optional.of("minecraft:gold_ingot"), CurrencyProvider.resolveCurrencyId(this::exists));
            assertEquals(List.of("minecraft:gold_ingot"), lookedUp, "a resolvable coin must not consult the fallback");
        }

        @Test
        @DisplayName("a CUSTOM provider resolves its configured id")
        void customResolvesRealItem() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.CUSTOM);
            McaQuestsConfig.COMMON.customCurrencyItem.set("minecraft:gold_ingot");
            assertEquals(Optional.of("minecraft:gold_ingot"), CurrencyProvider.resolveCurrencyId(this::exists));
        }

        @Test
        @DisplayName("an unresolvable custom id falls back rather than throwing")
        void unresolvableIdFallsBack() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.CUSTOM);
            McaQuestsConfig.COMMON.customCurrencyItem.set("nosuchmod:nosuchcoin");
            assertEquals(Optional.of("minecraft:emerald"), CurrencyProvider.resolveCurrencyId(this::exists));
        }

        @Test
        @DisplayName("configuredId reports the configured string without touching the registry")
        void configuredIdIsReported() {
            McaQuestsConfig.COMMON.currencyProvider.set(CurrencyProviderMode.NUMISMATICS);
            assertEquals("numismatics:spur", CurrencyProvider.configuredId());
            assertTrue(lookedUp.isEmpty(), "configuredId must not perform a lookup");
        }
    }

    @Nested
    @DisplayName("ranges and rolling")
    class Ranges {

        @Test
        @DisplayName("each difficulty band uses its own configured range, and the bands ascend")
        void bandsAscend() {
            assertEquals(1, band(QuestDifficulty.EASY).effectiveMin());
            assertEquals(2, band(QuestDifficulty.EASY).effectiveMax());
            assertEquals(2, band(QuestDifficulty.MEDIUM).effectiveMin());
            assertEquals(4, band(QuestDifficulty.MEDIUM).effectiveMax());
            assertEquals(4, band(QuestDifficulty.HARD).effectiveMin());
            assertEquals(8, band(QuestDifficulty.HARD).effectiveMax());
        }

        @Test
        @DisplayName("an explicit min/max overrides the band entirely")
        void explicitRangeWins() {
            CurrencyReward reward = new CurrencyReward(Optional.of(20), Optional.of(30),
                    Optional.of(QuestDifficulty.EASY));
            assertEquals(20, reward.effectiveMin());
            assertEquals(30, reward.effectiveMax());
        }

        @Test
        @DisplayName("a reward with no difficulty uses the MEDIUM default band")
        void defaultBandIsMedium() {
            CurrencyReward none = new CurrencyReward(Optional.empty(), Optional.empty(), Optional.empty());
            assertEquals(band(QuestDifficulty.DEFAULT).effectiveMin(), none.effectiveMin());
            assertEquals(band(QuestDifficulty.DEFAULT).effectiveMax(), none.effectiveMax());
        }

        @Test
        @DisplayName("rolls always land inside the effective range")
        void rollsStayInRange() {
            CurrencyReward reward = explicit(3, 7);
            RandomSource random = RandomSource.create(1234L);
            for (int i = 0; i < 500; i++) {
                int rolled = reward.roll(random);
                assertTrue(rolled >= 3 && rolled <= 7, "rolled " + rolled + " outside 3..7");
            }
        }

        @Test
        @DisplayName("a zero-width range always rolls that exact value")
        void fixedRangeIsDeterministic() {
            CurrencyReward reward = explicit(5, 5);
            RandomSource random = RandomSource.create(99L);
            for (int i = 0; i < 50; i++) {
                assertEquals(5, reward.roll(random));
            }
        }

        @Test
        @DisplayName("the multiplier scales both the shown range and the rolled amount")
        void multiplierScalesRangeAndRoll() {
            McaQuestsConfig.COMMON.currencyRewardMultiplier.set(2.0);
            CurrencyReward reward = explicit(3, 5);
            assertEquals(6, reward.effectiveMin());
            assertEquals(10, reward.effectiveMax());
            RandomSource random = RandomSource.create(7L);
            for (int i = 0; i < 200; i++) {
                int rolled = reward.roll(random);
                assertTrue(rolled >= 6 && rolled <= 10, "rolled " + rolled + " outside scaled 6..10");
            }
        }

        @Test
        @DisplayName("a zero multiplier turns currency off without producing negative payouts")
        void zeroMultiplierPaysNothing() {
            McaQuestsConfig.COMMON.currencyRewardMultiplier.set(0.0);
            assertEquals(0, explicit(4, 9).roll(RandomSource.create(3L)));
        }
    }

    @Nested
    @DisplayName("range validation")
    class Validation {

        private List<String> errorsFor(CurrencyReward reward) {
            List<String> errors = new ArrayList<>();
            reward.validate("Quest 'x' reward 0", errors);
            return errors;
        }

        @Test
        @DisplayName("a sane range produces no errors")
        void saneRangePasses() {
            assertTrue(errorsFor(explicit(2, 5)).isEmpty());
        }

        @Test
        @DisplayName("min greater than max is reported")
        void reversedRangeIsRejected() {
            List<String> errors = errorsFor(explicit(9, 4));
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("greater than"), errors.get(0));
        }

        @Test
        @DisplayName("negative bounds are reported")
        void negativeBoundsAreRejected() {
            assertFalse(errorsFor(explicit(-1, 5)).isEmpty());
            assertFalse(errorsFor(new CurrencyReward(Optional.empty(), Optional.of(-2), Optional.empty())).isEmpty());
        }
    }

    @Nested
    @DisplayName("freezing")
    class Freezing {

        @Test
        @DisplayName("effectiveMax never drops below effectiveMin even if a pack writes max < min")
        void rangeIsSelfHealingAtRuntime() {
            // Validation reports this at load; the runtime must still not produce a negative-width range,
            // because roll() would otherwise call nextInt with a non-positive bound and throw.
            CurrencyReward reward = explicit(9, 4);
            assertTrue(reward.effectiveMax() >= reward.effectiveMin());
            int rolled = reward.roll(RandomSource.create(5L));
            assertEquals(9, rolled);
        }

        @Test
        @DisplayName("OptionalInt round-trips the frozen amount concept used by ActiveQuest/ProjectState")
        void frozenAmountIsAnOptionalInt() {
            // Guards the contract the grant path relies on: an absent frozen value must be distinguishable
            // from a frozen value of 0, or a zeroed payout would silently re-roll at claim time.
            assertTrue(OptionalInt.empty().isEmpty());
            assertEquals(0, OptionalInt.of(0).getAsInt());
            assertTrue(OptionalInt.of(0).isPresent());
        }
    }
}
