package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.compat.bountiful.BountifulBridge;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireCapabilities;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireRegistryManifest;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the Ice &amp; Fire quest pack is mounted, decided the way the pack finder decides it.
 *
 * <p>Mounting is otherwise only observable in a running game, long after the decision was made, which
 * is exactly the sort of thing that ships wrong: a pack that never appears looks identical to a pack
 * whose content nobody has found yet.
 */
class ConditionalCompatPackTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** Stands in for the real provider so the test says nothing about what is on the classpath. */
    private record FakeIceAndFire(boolean core) implements CompatProvider {

        @Override
        public String id() {
            return IceAndFireRegistryManifest.MOD_ID;
        }

        @Override
        public Component displayName() {
            return Component.literal("Ice and Fire");
        }

        @Override
        public Set<String> namespaces() {
            return Set.of(IceAndFireRegistryManifest.MOD_ID);
        }

        @Override
        public CompatStatus status() {
            return core ? CompatStatus.FULL : CompatStatus.ABSENT;
        }

        @Override
        public List<CompatCapability> capabilities() {
            return List.of(new CompatCapability(IceAndFireCapabilities.CORE, core,
                    CapabilityEvidence.REGISTRY_CONFIRMED));
        }

        @Override
        public void reprobe(@Nullable RegistryAccess access) {
        }
    }

    /**
     * Stands in for the real Bountiful provider. Only the board capability is varied, because that is
     * the only one either Bountiful pack requires: the quests are all about a board, and the pools are
     * only worth mounting for a Bountiful that has one.
     */
    private record FakeBountiful(boolean board) implements CompatProvider {

        @Override
        public String id() {
            return BountifulBridge.MOD_ID;
        }

        @Override
        public Component displayName() {
            return Component.literal("Bountiful");
        }

        @Override
        public Set<String> namespaces() {
            return Set.of(BountifulBridge.MOD_ID);
        }

        @Override
        public CompatStatus status() {
            return board ? CompatStatus.FULL : CompatStatus.ABSENT;
        }

        @Override
        public List<CompatCapability> capabilities() {
            return List.of(new CompatCapability(BountifulBridge.Capability.BOARD_REGISTRY.id(), board,
                    CapabilityEvidence.REGISTRY_CONFIRMED));
        }

        @Override
        public void reprobe(@Nullable RegistryAccess access) {
        }
    }

    @BeforeEach
    void isolate() {
        CompatRegistry.get().clearForTest();
    }

    @AfterEach
    void reset() {
        CompatRegistry.get().clearForTest();
        CompatPacks.setIceAndFireBuiltinContentForTest(null);
        CompatPacks.setBountifulBuiltinContentForTest(null);
        CompatPacks.setBountifulIceAndFirePoolsForTest(null);
    }

    @Test
    @DisplayName("no Ice & Fire, no pack — whatever the config says")
    void absentModIsNeverMounted() {
        CompatPacks.setIceAndFireBuiltinContentForTest(() -> true);
        assertFalse(CompatPacks.ICEANDFIRE_QUESTS.isEnabled(CompatRegistry.get()),
                "with no provider registered there is nothing for the quests to be about");

        CompatRegistry.get().register(new FakeIceAndFire(false));
        assertFalse(CompatPacks.ICEANDFIRE_QUESTS.isEnabled(CompatRegistry.get()),
                "an installation with no dragon registered is the same as no installation");
    }

    @Test
    @DisplayName("Ice & Fire present but built-in content switched off — no pack")
    void switchedOffContentIsNotMounted() {
        CompatRegistry.get().register(new FakeIceAndFire(true));
        CompatPacks.setIceAndFireBuiltinContentForTest(() -> false);
        assertFalse(CompatPacks.ICEANDFIRE_QUESTS.isEnabled(CompatRegistry.get()),
                "a server that writes its own dragon quests keeps the capability and loses our content");
    }

    @Test
    @DisplayName("Ice & Fire present and content enabled — the pack is mounted")
    void presentAndEnabledIsMounted() {
        CompatRegistry.get().register(new FakeIceAndFire(true));
        CompatPacks.setIceAndFireBuiltinContentForTest(() -> true);
        assertTrue(CompatPacks.ICEANDFIRE_QUESTS.isEnabled(CompatRegistry.get()));
    }

    @Test
    @DisplayName("no Bountiful board, no Bountiful pack — whatever the config says")
    void bountifulNeedsABoard() {
        CompatPacks.setBountifulBuiltinContentForTest(() -> true);
        CompatPacks.setBountifulIceAndFirePoolsForTest(() -> true);
        assertFalse(CompatPacks.BOUNTIFUL_CORE.isEnabled(CompatRegistry.get()),
                "with no provider registered there is no board for these quests to be about");

        CompatRegistry.get().register(new FakeBountiful(false));
        assertFalse(CompatPacks.BOUNTIFUL_CORE.isEnabled(CompatRegistry.get()),
                "a Bountiful whose board did not register would send players looking for nothing");
    }

    @Test
    @DisplayName("Bountiful present but built-in content switched off — no pack")
    void bountifulContentCanBeSwitchedOff() {
        CompatRegistry.get().register(new FakeBountiful(true));
        CompatPacks.setBountifulBuiltinContentForTest(() -> false);
        assertFalse(CompatPacks.BOUNTIFUL_CORE.isEnabled(CompatRegistry.get()));
    }

    @Test
    @DisplayName("Bountiful present and content enabled — the core pack is mounted")
    void bountifulCoreIsMounted() {
        CompatRegistry.get().register(new FakeBountiful(true));
        CompatPacks.setBountifulBuiltinContentForTest(() -> true);
        assertTrue(CompatPacks.BOUNTIFUL_CORE.isEnabled(CompatRegistry.get()));
    }

    @Test
    @DisplayName("the Ice & Fire pools need all four of their conditions")
    void bountifulIceAndFireNeedsEverything() {
        CompatPacks.setBountifulBuiltinContentForTest(() -> true);
        CompatPacks.setBountifulIceAndFirePoolsForTest(() -> true);

        CompatRegistry.get().register(new FakeBountiful(true));
        assertFalse(CompatPacks.BOUNTIFUL_ICEANDFIRE.isEnabled(CompatRegistry.get()),
                "Bountiful alone would put Ice & Fire ids into another mod's generator that resolve "
                        + "to nothing");

        CompatRegistry.get().register(new FakeIceAndFire(true));
        assertTrue(CompatPacks.BOUNTIFUL_ICEANDFIRE.isEnabled(CompatRegistry.get()));

        CompatPacks.setBountifulIceAndFirePoolsForTest(() -> false);
        assertFalse(CompatPacks.BOUNTIFUL_ICEANDFIRE.isEnabled(CompatRegistry.get()),
                "the pools have their own switch because they join Bountiful's economy, not ours");

        CompatPacks.setBountifulIceAndFirePoolsForTest(() -> true);
        CompatPacks.setBountifulBuiltinContentForTest(() -> false);
        assertFalse(CompatPacks.BOUNTIFUL_ICEANDFIRE.isEnabled(CompatRegistry.get()),
                "turning our Bountiful content off turns all of it off");

        CompatPacks.setBountifulBuiltinContentForTest(() -> true);
        CompatRegistry.get().register(new FakeBountiful(false));
        assertFalse(CompatPacks.BOUNTIFUL_ICEANDFIRE.isEnabled(CompatRegistry.get()),
                "without a board there is nothing to generate the bounties at");
    }

    @Test
    @DisplayName("every declared pack has a folder that exists and carries a pack.mcmeta")
    void declaredPacksShip() {
        for (ConditionalCompatPack pack : CompatPacks.all()) {
            java.nio.file.Path root = java.nio.file.Path.of("src/main/resources/compatpacks", pack.folder());
            assertTrue(java.nio.file.Files.isRegularFile(root.resolve("pack.mcmeta")),
                    pack.id() + " has no pack.mcmeta at " + root.toAbsolutePath()
                            + "; Pack.readMetaAndCreate would answer null and the pack would "
                            + "silently never mount");
        }
    }
}
