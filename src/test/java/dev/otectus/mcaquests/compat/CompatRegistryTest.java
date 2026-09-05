package dev.otectus.mcaquests.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.otectus.mcaquests.support.TestBootstrap;
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
 * The four questions the rest of the mod asks {@link CompatRegistry}, answered against a provider
 * that exists only here — so the test says nothing about whether Townstead or FTB Quests happen to be
 * on the test classpath, which is the whole point of the seam.
 */
class CompatRegistryTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** A provider with one present and one absent capability, counting its own re-probes. */
    private static final class FakeProvider implements CompatProvider {

        private int reprobes;

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
            return Set.of("fakemod", "fakemod_extra");
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
            reprobes++;
        }
    }

    private FakeProvider fake;

    @BeforeEach
    void register() {
        CompatRegistry.get().clearForTest();
        fake = new FakeProvider();
        CompatRegistry.get().register(fake);
    }

    @AfterEach
    void reset() {
        CompatRegistry.get().clearForTest();
    }

    @Test
    @DisplayName("has() answers per capability, and 'no' for anything it has never heard of")
    void has() {
        assertTrue(CompatRegistry.get().has("fakemod", "core"));
        assertFalse(CompatRegistry.get().has("fakemod", "extras"));
        assertFalse(CompatRegistry.get().has("fakemod", "invented"));
        assertFalse(CompatRegistry.get().has("othermod", "core"));
    }

    @Test
    @DisplayName("forNamespace finds a provider by any namespace it claims")
    void forNamespace() {
        assertEquals("fakemod", CompatRegistry.get().forNamespace("fakemod").orElseThrow().id());
        assertEquals("fakemod", CompatRegistry.get().forNamespace("fakemod_extra").orElseThrow().id());
        assertTrue(CompatRegistry.get().forNamespace("minecraft").isEmpty());
    }

    @Test
    @DisplayName("describeMissing names the mod when one owns the namespace, the namespace when none does")
    void describeMissing() {
        Component known = CompatRegistry.get().describeMissing(ResourceLocation.fromNamespaceAndPath("fakemod", "wyvern"));
        assertEquals("mcaquests.objective.unavailable.compat",
                ((net.minecraft.network.chat.contents.TranslatableContents) known.getContents()).getKey());
        assertEquals(List.of(Component.literal("Fake Mod")),
                List.of(((net.minecraft.network.chat.contents.TranslatableContents) known.getContents())
                        .getArgs()[0]));

        Component unknown = CompatRegistry.get().describeMissing(ResourceLocation.fromNamespaceAndPath("nobody", "thing"));
        assertEquals("nobody",
                ((net.minecraft.network.chat.contents.TranslatableContents) unknown.getContents()).getArgs()[0]);
    }

    @Test
    @DisplayName("reprobeAll reaches every provider and counts the pass")
    void reprobeAll() {
        int before = CompatRegistry.get().reprobeCount();
        CompatRegistry.get().reprobeAll("test", null);
        CompatRegistry.get().reprobeAll("test", null);
        assertEquals(2, fake.reprobes);
        assertEquals(before + 2, CompatRegistry.get().reprobeCount());
    }

    @Test
    @DisplayName("a provider that throws is skipped, not propagated")
    void reprobeSwallowsFailure() {
        CompatRegistry.get().register(new CompatProvider() {
            @Override
            public String id() {
                return "brokenmod";
            }

            @Override
            public Component displayName() {
                return Component.literal("Broken Mod");
            }

            @Override
            public Set<String> namespaces() {
                return Set.of("brokenmod");
            }

            @Override
            public CompatStatus status() {
                return CompatStatus.DISABLED;
            }

            @Override
            public List<CompatCapability> capabilities() {
                return List.of();
            }

            @Override
            public void reprobe(@Nullable RegistryAccess access) {
                throw new IllegalStateException("boom");
            }
        });
        CompatRegistry.get().reprobeAll("test", null);
        assertEquals(2, CompatRegistry.get().providers().size());
    }
}
