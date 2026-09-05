package dev.otectus.mcaquests.quest.condition;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.quest.condition.leaf.CompatCapabilityCondition;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mcaquests:compat_capability} — the gate a pack uses to ship content for a mod that may not
 * be installed. {@code test(...)} never touches the {@code QuestContext}, so these cases can pass
 * {@code null} and still exercise exactly what the condition does.
 */
class CompatCapabilityConditionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @BeforeEach
    void register() {
        CompatRegistry.get().clearForTest();
        CompatRegistry.get().register(new CompatProvider() {
            @Override
            public String id() {
                return "fakemod";
            }

            @Override
            public Component displayName() {
                return Component.literal("Fake Mod");
            }

            @Override
            public Set<String> namespaces() {
                return Set.of("fakemod");
            }

            @Override
            public CompatStatus status() {
                return CompatStatus.PARTIAL;
            }

            @Override
            public List<CompatCapability> capabilities() {
                return List.of(
                        new CompatCapability("core", true, CapabilityEvidence.REGISTRY_CONFIRMED),
                        new CompatCapability("extras", false, CapabilityEvidence.REGISTRY_CONFIRMED));
            }

            @Override
            public void reprobe(@Nullable RegistryAccess access) {
            }
        });
    }

    @AfterEach
    void reset() {
        CompatRegistry.get().clearForTest();
    }

    private static CompatCapabilityCondition parse(String json) {
        return CompatCapabilityCondition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
    }

    @Test
    @DisplayName("present defaults to true, so the common case needs no field")
    void presentDefaultsTrue() {
        CompatCapabilityCondition condition =
                parse("{\"provider\":\"fakemod\",\"capability\":\"core\"}");
        assertTrue(condition.present());
        assertTrue(condition.test(null));
    }

    @Test
    @DisplayName("an absent capability fails the gate")
    void absentCapability() {
        assertFalse(parse("{\"provider\":\"fakemod\",\"capability\":\"extras\"}").test(null));
    }

    @Test
    @DisplayName("present=false inverts it, which is how a pack ships the fallback quest")
    void inverted() {
        assertTrue(parse("{\"provider\":\"fakemod\",\"capability\":\"extras\",\"present\":false}").test(null));
        assertFalse(parse("{\"provider\":\"fakemod\",\"capability\":\"core\",\"present\":false}").test(null));
    }

    @Test
    @DisplayName("an unknown provider is 'not present', never a parse failure")
    void unknownProvider() {
        assertFalse(parse("{\"provider\":\"nobody\",\"capability\":\"core\"}").test(null));
        assertTrue(parse("{\"provider\":\"nobody\",\"capability\":\"core\",\"present\":false}").test(null));
    }

    @Test
    @DisplayName("the type is registered under mcaquests:compat_capability")
    void registered() {
        assertEquals("mcaquests:compat_capability", ConditionTypes.COMPAT_CAPABILITY.id().toString());
    }
}
