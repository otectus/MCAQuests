package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InteractBlockObjective}'s codec, its block/tag exclusivity, and its tolerance of a block
 * this world does not have.
 *
 * <p>Two behaviours here are the reason the type exists at all. The tolerance keeps a quest naming a
 * bounty board from failing to parse on an installation without Bountiful — it suspends with its
 * title and progress intact instead. The exclusivity is caught at parse time because "neither" and
 * "both" have no sensible runtime meaning: one would match nothing forever, the other would leave a
 * pack author guessing which field won.
 */
class InteractBlockObjectiveTest {

    private static final ResourceLocation STONE = new ResourceLocation("minecraft", "stone");
    private static final ResourceLocation ABSENT = new ResourceLocation("bountiful", "bountyboard");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static DataResult<InteractBlockObjective> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return InteractBlockObjective.CODEC.parse(JsonOps.INSTANCE, element);
    }

    @Test
    @DisplayName("count defaults to one, and every field round-trips")
    void codecRoundTrip() {
        InteractBlockObjective defaults = parse("{\"block\":\"minecraft:stone\"}").result().orElseThrow();
        assertEquals(Optional.of(STONE), defaults.block());
        assertEquals(Optional.empty(), defaults.tag());
        assertEquals(1, defaults.count());
        assertEquals(Optional.empty(), defaults.source());

        InteractBlockObjective explicit =
                parse("{\"block\":\"minecraft:stone\",\"count\":3}").result().orElseThrow();
        assertEquals(3, explicit.count());

        JsonElement encoded = InteractBlockObjective.CODEC.encodeStart(JsonOps.INSTANCE, explicit)
                .result().orElseThrow();
        assertEquals(explicit,
                InteractBlockObjective.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    @DisplayName("a tag parses on its own and round-trips")
    void tagRoundTrips() {
        InteractBlockObjective byTag = parse("{\"tag\":\"minecraft:doors\"}").result().orElseThrow();
        assertEquals(Optional.empty(), byTag.block());
        assertTrue(byTag.tag().isPresent());

        JsonElement encoded = InteractBlockObjective.CODEC.encodeStart(JsonOps.INSTANCE, byTag)
                .result().orElseThrow();
        assertEquals(byTag,
                InteractBlockObjective.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    @DisplayName("exactly one of block and tag — neither and both are parse errors")
    void blockAndTagAreExclusive() {
        assertTrue(parse("{\"count\":2}").error().isPresent(),
                "an objective naming nothing would sit at zero forever with no explanation");
        assertTrue(parse("{\"block\":\"minecraft:stone\",\"tag\":\"minecraft:doors\"}").error().isPresent(),
                "with both set the pack author cannot tell which one is being used");
    }

    @Test
    @DisplayName("a registered block reports no reason to be unavailable, and matches its state")
    void registeredBlockIsAvailable() {
        InteractBlockObjective objective = parse("{\"block\":\"minecraft:stone\"}").result().orElseThrow();

        assertEquals(Optional.empty(), objective.unofferableReason(null));
        assertEquals(Optional.empty(), objective.unavailableReason(null, null, null, null));
        assertTrue(objective.matches(Blocks.STONE.defaultBlockState()));
        assertFalse(objective.matches(Blocks.DIRT.defaultBlockState()));
    }

    @Test
    @DisplayName("an unregistered block parses, never matches, and says why it is unavailable")
    void unregisteredBlockIsTolerated() {
        InteractBlockObjective objective =
                parse("{\"block\":\"bountiful:bountyboard\"}").result().orElseThrow();

        assertEquals(Optional.of(ABSENT), objective.block(),
                "the id is kept exactly as written, so the quest comes back when the mod does");
        assertTrue(objective.unofferableReason(null).isPresent(),
                "a quest about a block this world has never heard of must never be offered");
        assertTrue(objective.unavailableReason(null, null, null, null).isPresent(),
                "a copy already accepted suspends rather than fails");
        assertFalse(objective.matches(Blocks.STONE.defaultBlockState()));
    }

    @Test
    @DisplayName("a tag objective is never \"unavailable\" — an empty tag simply matches nothing")
    void tagsAreNeverUnavailable() {
        InteractBlockObjective objective = parse("{\"tag\":\"mcaquests:nothing_at_all\"}")
                .result().orElseThrow();

        assertEquals(Optional.empty(), objective.unofferableReason(null),
                "there is no mod to name as the reason, and an empty tag is a legitimate state");
        assertFalse(objective.matches(Blocks.STONE.defaultBlockState()));
    }

    @Test
    @DisplayName("the two-argument constructor an add-on would use still compiles")
    void legacyConstructorStillWorks() {
        InteractBlockObjective objective = new InteractBlockObjective(STONE, 2);
        assertEquals(Optional.of(STONE), objective.block());
        assertEquals(2, objective.count());
        assertEquals(Optional.empty(), objective.tag());
    }
}
