package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The villager selector and the per-objective binding that pins it to one concrete villager.
 *
 * <p>The binding is the fix for family quests naming one relative while highlighting another: a
 * {@code family} target resolves through MCA's family tree, which prefers whichever relative happens to be
 * loaded, so without a frozen id the same quest can mean different people from tick to tick.
 */
class VillagerTargetBindingTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static VillagerTarget parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<VillagerTarget> result = VillagerTarget.CODEC.parse(JsonOps.INSTANCE, element);
        return result.result().orElseThrow(() ->
                new AssertionError("expected " + json + " to parse, but got: " + result.error().orElseThrow().message()));
    }

    private static List<String> validationErrors(VillagerTarget target) {
        List<String> errors = new ArrayList<>();
        target.validate("test", errors);
        return errors;
    }

    @Nested
    @DisplayName("the codec")
    class Codec {

        @Test
        @DisplayName("every mode round-trips through JSON")
        void roundTrips() {
            List<VillagerTarget> targets = List.of(
                    VillagerTarget.SELF,
                    new VillagerTarget(VillagerTarget.Mode.PROFESSION,
                            Optional.of(ResourceLocation.withDefaultNamespace("weaponsmith")),
                            Optional.empty(), Optional.empty()),
                    new VillagerTarget(VillagerTarget.Mode.FAMILY, Optional.empty(),
                            Optional.of("grandparent"), Optional.empty()),
                    new VillagerTarget(VillagerTarget.Mode.UUID, Optional.empty(), Optional.empty(),
                            Optional.of(UUID.fromString("11111111-2222-3333-4444-555555555555"))));

            for (VillagerTarget target : targets) {
                JsonElement encoded = VillagerTarget.CODEC.encodeStart(JsonOps.INSTANCE, target)
                        .result().orElseThrow();
                assertEquals(target, VillagerTarget.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow(),
                        "a datapack value must survive being written back out and re-read");
            }
        }

        @Test
        @DisplayName("grandparent is an accepted family relation")
        void grandparentParses() {
            VillagerTarget target = parse("{\"mode\":\"family\",\"relation\":\"grandparent\"}");

            assertEquals(Optional.of("grandparent"), target.relation());
            assertTrue(validationErrors(target).isEmpty(),
                    "grandparent is resolvable through the family tree, so it must validate clean");
        }

        @Test
        @DisplayName("an unknown relation is reported once, naming the offending value")
        void unknownRelationRejected() {
            List<String> errors = validationErrors(parse("{\"mode\":\"family\",\"relation\":\"cousin\"}"));

            assertEquals(1, errors.size(), "one bad field should produce exactly one message");
            assertTrue(errors.get(0).contains("cousin"), "the message must name the value the author wrote");
        }

        @Test
        @DisplayName("uuid mode without a uuid is reported")
        void uuidModeNeedsAUuid() {
            assertEquals(1, validationErrors(parse("{\"mode\":\"uuid\"}")).size(),
                    "a target that can never resolve must be caught at datapack load, not at runtime");
        }
    }

    @Test
    @DisplayName("the target relations and the condition relations stay in step")
    void relationSetsAgree() {
        assertEquals(McaConditionCodecs.RELATED_RELATIONS, VillagerTarget.RELATIONS,
                "a quest gates on related_villager_status <relation> and then selects that same relation;"
                        + " if the two sets drift, a quest can be offered for a relation it cannot target");
    }

    @Nested
    @DisplayName("the per-objective binding")
    class Binding {

        @Test
        @DisplayName("an unbound objective writes no target tag")
        void unboundWritesNothing() {
            assertFalse(new ObjectiveProgress(3).save().contains("target"),
                    "saves from before the binding existed must round-trip byte-identically");
        }

        @Test
        @DisplayName("a pre-binding save loads unbound, and can then bind")
        void oldSaveLoadsUnbound() {
            CompoundTag legacy = new CompoundTag();
            legacy.putInt("count", 2); // exactly what an old save carried

            ObjectiveProgress loaded = ObjectiveProgress.load(legacy);

            assertNull(loaded.targetUuid(),
                    "an in-flight quest from an older save has no bound villager; it binds on its next tick");
            assertEquals(2, loaded.count(), "and its progress must be preserved across the upgrade");
        }

        @Test
        @DisplayName("a bound villager survives save and reload")
        void bindingSurvivesReload() {
            UUID bound = UUID.randomUUID();
            ObjectiveProgress progress = new ObjectiveProgress();
            progress.setTargetUuid(bound);

            assertEquals(bound, ObjectiveProgress.load(progress.save()).targetUuid(),
                    "reconnecting or reloading the world must not repoint the quest at a different relative");
        }
    }
}
