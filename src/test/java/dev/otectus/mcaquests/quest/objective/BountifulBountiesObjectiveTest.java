package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.bountiful.BountifulBinding;
import dev.otectus.mcaquests.compat.bountiful.BountifulBridge;
import dev.otectus.mcaquests.compat.bountiful.BountifulCompat;
import dev.otectus.mcaquests.compat.bountiful.BountifulHookProbe;
import dev.otectus.mcaquests.compat.bountiful.BountyCompletion;
import dev.otectus.mcaquests.compat.bountiful.BountyRarity;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BountifulBountiesObjective}'s codec, its rarity ordering, and the two reasons it can refuse
 * to be counted.
 *
 * <p>The reasons are the point. This is the one objective in the mod that cannot work on its own, and
 * both ways it can fail are silent: without the cash-in hook nothing would ever advance it, and
 * without the rarity reader a {@code min_rarity} quest would be satisfiable by the easiest bounty on
 * the board. Neither shows up as an error anywhere — they show up as a player saying the quest is
 * broken — so both are asserted here against a real bridge rather than a mock.
 */
class BountifulBountiesObjectiveTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @AfterEach
    void restoreRegistry() {
        CompatRegistry.get().clearForTest();
    }

    /**
     * Installs the bridge {@code select} would build from these facts, so the objective is asked the
     * same question production asks it. An empty resolution means nothing bound, which is exactly the
     * "the hook is there but the rarity reader is not" installation.
     */
    private static void installBridge(boolean hookAvailable) {
        BountifulCompat compat = new BountifulCompat();
        compat.setBridgeForTest(BountifulCompat.select(true, McaQuestsConfig.BountifulMode.AUTO,
                hookAvailable, BountifulHookProbe.State.UNKNOWN, BountifulBinding.absent()));
        CompatRegistry.get().register(compat);
    }

    private static DataResult<BountifulBountiesObjective> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return BountifulBountiesObjective.CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static BountyCompletion completedAt(BountyRarity rarity) {
        return new BountyCompletion(UUID.randomUUID(), 0L, rarity.name(), 1, "key");
    }

    @Test
    @DisplayName("count and min_rarity round-trip, and the rarity is case-insensitive")
    void codecRoundTrip() {
        BountifulBountiesObjective defaults = parse("{}").result().orElseThrow();
        assertEquals(1, defaults.count());
        assertEquals(Optional.empty(), defaults.minRarity());
        assertEquals(Optional.empty(), defaults.source());

        BountifulBountiesObjective explicit =
                parse("{\"count\":2,\"min_rarity\":\"RARE\"}").result().orElseThrow();
        assertEquals(2, explicit.count());
        assertEquals(Optional.of(BountyRarity.RARE), explicit.minRarity());

        assertEquals(Optional.of(BountyRarity.EPIC),
                parse("{\"min_rarity\":\"epic\"}").result().orElseThrow().minRarity());
        assertEquals(Optional.of(BountyRarity.LEGENDARY),
                parse("{\"min_rarity\":\"Legendary\"}").result().orElseThrow().minRarity());

        JsonElement encoded = BountifulBountiesObjective.CODEC
                .encodeStart(JsonOps.INSTANCE, explicit).result().orElseThrow();
        assertEquals(explicit, parse(encoded.toString()).result().orElseThrow());
    }

    @Test
    @DisplayName("UNKNOWN is not a rarity a pack may ask for")
    void unknownRarityIsRejected() {
        assertTrue(parse("{\"min_rarity\":\"UNKNOWN\"}").error().isPresent(),
                "UNKNOWN is this mod's word for \"we could not read it\", so a quest requiring it "
                        + "could never be satisfied by anything -- which the pack author should hear "
                        + "at parse time, the only moment they can fix it");
        assertTrue(parse("{\"min_rarity\":\"mythic\"}").error().isPresent());
    }

    @Test
    @DisplayName("the count-only constructor an add-on would use still compiles")
    void legacyConstructor() {
        BountifulBountiesObjective objective = new BountifulBountiesObjective(3);
        assertEquals(3, objective.count());
        assertEquals(Optional.empty(), objective.minRarity());
        assertEquals(3, objective.required());
    }

    @Test
    @DisplayName("a RARE minimum is met from RARE upwards, and never by an unreadable rarity")
    void rarityOrdering() {
        BountifulBountiesObjective rare =
                parse("{\"min_rarity\":\"RARE\"}").result().orElseThrow();

        assertFalse(rare.matches(completedAt(BountyRarity.COMMON)));
        assertFalse(rare.matches(completedAt(BountyRarity.UNCOMMON)));
        assertTrue(rare.matches(completedAt(BountyRarity.RARE)));
        assertTrue(rare.matches(completedAt(BountyRarity.EPIC)));
        assertTrue(rare.matches(completedAt(BountyRarity.LEGENDARY)));
        assertFalse(rare.matches(completedAt(BountyRarity.UNKNOWN)),
                "a rarity we could not read must never satisfy a minimum; treating it as the lowest "
                        + "rank would make the specialist quest satisfiable by anything");

        BountifulBountiesObjective any = new BountifulBountiesObjective(1);
        assertTrue(any.matches(completedAt(BountyRarity.UNKNOWN)),
                "with no minimum asked for, an unreadable rarity is not a problem");
    }

    @Test
    @DisplayName("satisfied only once the count is reached")
    void satisfaction() {
        BountifulBountiesObjective objective = new BountifulBountiesObjective(2);
        ObjectiveProgress progress = new ObjectiveProgress();

        assertFalse(objective.isSatisfied(null, progress));
        progress.add(1);
        assertFalse(objective.isSatisfied(null, progress));
        progress.add(1);
        assertTrue(objective.isSatisfied(null, progress));
        assertEquals(2, objective.current(null, progress));
        progress.add(5);
        assertEquals(2, objective.current(null, progress), "progress is reported against the ask");
    }

    @Test
    @DisplayName("without the cash-in hook, the hook reason -- not the rarity one")
    void noHookIsTheHookReason() {
        installBridge(false);

        assertEquals(Optional.of("mcaquests.objective.unavailable.bountiful_hook"),
                reasonKey(parse("{\"count\":2,\"min_rarity\":\"RARE\"}").result().orElseThrow()));
        assertEquals(Optional.of("mcaquests.objective.unavailable.bountiful_hook"),
                reasonKey(new BountifulBountiesObjective(1)),
                "nothing can be counted at all, whether or not a rarity was asked for");
    }

    @Test
    @DisplayName("with the hook but no rarity reader, only a min_rarity objective is unavailable")
    void rarityReaderIsItsOwnReason() {
        installBridge(true);

        assertEquals(Optional.of("mcaquests.objective.unavailable.bountiful_rarity"),
                reasonKey(parse("{\"min_rarity\":\"RARE\"}").result().orElseThrow()));
        assertEquals(Optional.empty(), reasonKey(new BountifulBountiesObjective(2)),
                "plain counting still works, so a count-only quest must keep being offered");
    }

    /** The translation key of the reported reason, or empty when there is none. */
    private static Optional<String> reasonKey(BountifulBountiesObjective objective) {
        return objective.unofferableReason(null)
                .map(component -> ((net.minecraft.network.chat.contents.TranslatableContents)
                        component.getContents()).getKey());
    }
}
