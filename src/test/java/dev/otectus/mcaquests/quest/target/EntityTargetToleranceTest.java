package dev.otectus.mcaquests.quest.target;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An entity id this world does not have must survive the parse.
 *
 * <p>Before 1.5.4 it did not: one quest naming a creature from an uninstalled mod failed to load, and
 * a player who already held that quest saw "Unknown quest" with no way back. These cases are the
 * contract that replaced that — the id is kept, the target reports itself unresolved, and encoding
 * hands the same id back so nothing is silently dropped on a round trip.
 */
class EntityTargetToleranceTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static EntityTarget parse(String json) {
        return EntityTarget.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(AssertionError::new);
    }

    @Test
    @DisplayName("an unknown id decodes to an unresolved target instead of failing")
    void unknownIdIsUnresolved() {
        EntityTarget target = parse("{\"entity\":\"iceandfire:hydra\"}");
        assertTrue(target.isUnresolved());
        assertEquals(ResourceLocation.fromNamespaceAndPath("iceandfire", "hydra"), target.unresolved().orElseThrow());
        assertTrue(target.entity().isEmpty());
    }

    @Test
    @DisplayName("a known id still resolves to its entity type")
    void knownIdResolves() {
        EntityTarget target = parse("{\"entity\":\"minecraft:zombie\"}");
        assertFalse(target.isUnresolved());
        assertEquals(EntityType.ZOMBIE, target.entity().orElseThrow());
    }

    @Test
    @DisplayName("an unresolved target matches nothing, so nothing can be credited for it")
    void unresolvedMatchesNothing() {
        // No Entity instance is needed to prove the guard: matches() returns before touching it.
        assertFalse(parse("{\"entity\":\"iceandfire:hydra\"}").matches(null));
    }

    @Test
    @DisplayName("encoding writes the unresolved id back unchanged")
    void encodeRoundTrips() {
        for (String id : new String[]{"iceandfire:hydra", "minecraft:zombie"}) {
            String json = "{\"entity\":\"" + id + "\"}";
            EntityTarget target = parse(json);
            JsonElement encoded = EntityTarget.MAP_CODEC.codec()
                    .encodeStart(JsonOps.INSTANCE, target)
                    .getOrThrow(AssertionError::new);
            assertEquals(JsonParser.parseString(json), encoded);
        }
    }

    @Test
    @DisplayName("the pre-1.5.4 two-argument constructor still compiles and means 'resolved'")
    void legacyConstructor() {
        EntityTarget target = new EntityTarget(Optional.of(EntityType.ZOMBIE), Optional.empty());
        assertFalse(target.isUnresolved());
        assertTrue(target.unresolved().isEmpty());
    }

    @Test
    @DisplayName("describe() names the missing id rather than a blank")
    void describeNamesTheId() {
        assertEquals("mcaquests.target.unavailable",
                ((net.minecraft.network.chat.contents.TranslatableContents)
                        parse("{\"entity\":\"iceandfire:hydra\"}").describe().getContents()).getKey());
    }
}
