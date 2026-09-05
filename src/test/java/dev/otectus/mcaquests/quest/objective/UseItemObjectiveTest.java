package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UseItemObjective}'s codec and its tolerance of an item this world does not have.
 *
 * <p>The tolerance is the point of the type: the quests that use it name items from an optional mod,
 * so "the id does not resolve" must be an answer the objective gives rather than a parse failure that
 * takes the whole quest with it.
 */
class UseItemObjectiveTest {

    private static final ResourceLocation APPLE = new ResourceLocation("minecraft", "apple");
    private static final ResourceLocation ABSENT = new ResourceLocation("iceandfire", "legendary_dragon_seeker");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static DataResult<UseItemObjective> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return UseItemObjective.CODEC.parse(JsonOps.INSTANCE, element);
    }

    @Test
    @DisplayName("require_success defaults to false, and every field round-trips")
    void codecRoundTrip() {
        UseItemObjective defaults = parse("{\"item\":\"minecraft:apple\"}").result().orElseThrow();
        assertEquals(APPLE, defaults.item());
        assertEquals(1, defaults.count());
        assertFalse(defaults.requireSuccess(), "a seeker-style item has no use duration, so the "
                + "objective must credit a plain right-click unless the pack says otherwise");
        assertEquals(Optional.empty(), defaults.source());

        UseItemObjective explicit = parse(
                "{\"item\":\"minecraft:apple\",\"count\":3,\"require_success\":true}")
                .result().orElseThrow();
        assertEquals(3, explicit.count());
        assertTrue(explicit.requireSuccess());

        JsonElement encoded = UseItemObjective.CODEC.encodeStart(JsonOps.INSTANCE, explicit)
                .result().orElseThrow();
        assertEquals(explicit, UseItemObjective.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    @DisplayName("the legacy two-argument constructor still compiles and means what it did")
    void legacyConstructor() {
        UseItemObjective built = new UseItemObjective(APPLE, 2);
        assertEquals(new UseItemObjective(APPLE, 2, false, Optional.empty()), built);
    }

    @Test
    @DisplayName("an item this world does not have is unofferable and unavailable, not a parse error")
    void unregisteredItemIsReported() {
        DataResult<UseItemObjective> parsed = parse("{\"item\":\"iceandfire:legendary_dragon_seeker\"}");
        assertTrue(parsed.result().isPresent(), "the id must survive the codec so the quest keeps its title");
        UseItemObjective objective = parsed.result().get();
        assertEquals(ABSENT, objective.item());
        assertTrue(objective.unofferableReason(null).isPresent());
        assertTrue(objective.unavailableReason(null, null, new ObjectiveProgress(), null).isPresent());
    }

    @Test
    @DisplayName("a registered item has nothing to report")
    void registeredItemIsSilent() {
        UseItemObjective objective = new UseItemObjective(APPLE, 1);
        assertEquals(Optional.empty(), objective.unofferableReason(null));
        assertEquals(Optional.empty(),
                objective.unavailableReason(null, null, new ObjectiveProgress(), null));
    }

    @Test
    @DisplayName("satisfaction is the counted uses reaching the required count")
    void satisfiedAtCount() {
        UseItemObjective objective = new UseItemObjective(APPLE, 3);
        ObjectiveProgress progress = new ObjectiveProgress();
        assertFalse(objective.isSatisfied(null, progress));
        progress.add(2);
        assertFalse(objective.isSatisfied(null, progress));
        assertEquals(2, objective.current(null, progress));
        progress.add(1);
        assertTrue(objective.isSatisfied(null, progress));
        progress.add(5);
        assertEquals(3, objective.current(null, progress), "progress past the requirement still "
                + "displays as the requirement");
    }
}
