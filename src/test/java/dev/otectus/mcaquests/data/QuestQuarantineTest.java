package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to a quest a player is holding when its definition stops loading.
 *
 * <p>The old answer was "Unknown quest": the log showed a raw id, the player could not tell whether
 * removing a mod had caused it, and the quest's deadline kept running down. The quarantine is the
 * replacement — the load records which namespace was blamed, and the log turns that into a paused
 * quest naming the mod it is waiting for.
 */
class QuestQuarantineTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUARANTINED = ResourceLocation.fromNamespaceAndPath("mcaquests", "hunt/hydra");
    private static final ResourceLocation IN_COMPAT_PACK =
            ResourceLocation.fromNamespaceAndPath("mcaquests", "compat/fakemod/dragon_hunt");

    @BeforeEach
    void setUp() {
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
                return CompatStatus.ABSENT;
            }

            @Override
            public List<CompatCapability> capabilities() {
                return List.of(new CompatCapability("core", false, CapabilityEvidence.REGISTRY_CONFIRMED));
            }

            @Override
            public void reprobe(@Nullable RegistryAccess access) {
            }
        });
    }

    @AfterEach
    void tearDown() {
        CompatRegistry.get().clearForTest();
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("a quarantined id remembers the namespace its parse error blamed")
    void quarantineRecordsNamespace() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of(), Map.of(QUARANTINED, "fakemod"));
        assertTrue(QuestRegistry.isQuarantined(QUARANTINED));
        assertEquals("fakemod", QuestRegistry.quarantinedNamespace(QUARANTINED).orElseThrow());
    }

    @Test
    @DisplayName("the next load clears it, so a fixed pack needs no other action")
    void clearedByNextLoad() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of(), Map.of(QUARANTINED, "fakemod"));
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of());
        assertFalse(QuestRegistry.isQuarantined(QUARANTINED));
        assertTrue(QuestRegistry.quarantinedNamespace(QUARANTINED).isEmpty());
    }

    @Test
    @DisplayName("a quarantined quest is blamed on the provider that owns its namespace")
    void suspensionNamesTheProvider() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of(), Map.of(QUARANTINED, "fakemod"));
        assertEquals(Optional.of(Component.literal("Fake Mod")),
                QuestManager.compatSuspensionSubject(QUARANTINED));
    }

    @Test
    @DisplayName("a namespace no provider claims is reported as itself rather than guessed at")
    void suspensionFallsBackToTheNamespace() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of(), Map.of(QUARANTINED, "nobody"));
        assertEquals(Optional.of(Component.literal("nobody")),
                QuestManager.compatSuspensionSubject(QUARANTINED));
    }

    @Test
    @DisplayName("a quest under compat/<provider>/ is blamed on that provider even without a quarantine")
    void compatPackPathAlone() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of());
        assertEquals(Optional.of(Component.literal("Fake Mod")),
                QuestManager.compatSuspensionSubject(IN_COMPAT_PACK));
    }

    @Test
    @DisplayName("an ordinary deleted quest is not a compatibility problem")
    void plainMissingDefinition() {
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of());
        assertTrue(QuestManager.compatSuspensionSubject(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "farmer/harvest")).isEmpty());
        assertTrue(QuestManager.compatSuspensionSubject(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "compat/nobody/thing")).isEmpty());
    }
}
