package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MCA Capitals, as a {@link CompatProvider}: a thin shell that owns <em>which</em>
 * {@link CapitalsBridge} is in use and delegates everything else to it.
 *
 * <p>The choice is re-made on every {@link #reprobe}, which is what makes the integration follow the
 * installation rather than the moment the game started: switching {@code compat.capitals.enabled} off
 * and running {@code /reload} takes effect immediately, and a world opened with a different mod set
 * gets the bridge that fits it.
 *
 * <p>{@link #select} is deliberately a pure function of three facts, so the whole decision table can
 * be tested without Forge, a mod file or a running game.
 */
public final class CapitalsCompat implements CompatProvider {

    private volatile CapitalsBridge bridge = new NoopCapitalsBridge(false);

    @Override
    public String id() {
        return CapitalsBinding.MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.capitals.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(CapitalsBinding.MOD_ID);
    }

    @Override
    public CompatStatus status() {
        return bridge.status();
    }

    @Override
    public List<CompatCapability> capabilities() {
        return bridge.capabilities();
    }

    /** The bridge currently in use. Never null, and never the same object across a re-probe. */
    public CapitalsBridge currentBridge() {
        return bridge;
    }

    /**
     * Test seam: use {@code bridge} instead of whatever a probe would choose. Production only ever
     * sets this through {@link #reprobe}.
     */
    public void setBridgeForTest(CapitalsBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * The bridge the registered provider is using, or a permanently-absent one when Capitals has no
     * provider registered at all. The entry point for everything outside this package.
     */
    public static CapitalsBridge bridge(CompatRegistry registry) {
        return registry.provider(CapitalsBinding.MOD_ID).orElse(null) instanceof CapitalsCompat compat
                ? compat.currentBridge()
                : new NoopCapitalsBridge(false);
    }

    /** As {@link #bridge(CompatRegistry)}, against the singleton registry. */
    public static CapitalsBridge bridge() {
        return bridge(CompatRegistry.get());
    }

    /**
     * Whether MCA: Quests' own Capitals quest pack may be mounted.
     *
     * <p>Separate from {@code enabled} for the same reason Bountiful's is: a server that writes its
     * own court quests wants the capabilities and the {@code compat_capability} condition but not our
     * content. Never affects what {@link #capabilities()} reports.
     */
    public static boolean builtinContentEnabled() {
        return McaQuestsConfig.COMMON.capitalsEnableBuiltinContent.get();
    }

    /**
     * Re-decides which bridge is in use.
     *
     * <p>{@code access} is unused: Capitals keeps its capitals in an in-memory map mirrored into saved
     * data, not in a dynamic registry, and every read goes through a handle at the moment it is asked.
     * Nothing is cached here that a datapack reload could invalidate.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
        boolean loaded = ModList.get().isLoaded(CapitalsBinding.MOD_ID);
        boolean enabled = McaQuestsConfig.COMMON.capitalsEnabled.get();
        CapitalsBinding.Resolution resolution = loaded && enabled
                ? CapitalsBinding.resolveAgainst(CapitalsCompat.class.getClassLoader())
                : CapitalsBinding.absent();
        bridge = select(loaded, enabled, resolution);
    }

    /**
     * Which bridge fits these three facts. Pure, total, and the whole of the decision.
     *
     * <p>{@code enabled == false} beats everything, because a switched-off integration must behave
     * exactly like an absent mod rather than like a degraded one — with one difference the owner can
     * see: it reports {@link CompatStatus#DISABLED}, so the person who switched it off months ago and
     * forgot is told so rather than left hunting for a missing jar.
     *
     * @param loaded     whether Forge reports Capitals installed
     * @param enabled    {@code compat.capitals.enabled}
     * @param resolution the resolved manifest, which decides the nine capabilities
     */
    public static CapitalsBridge select(boolean loaded, boolean enabled,
                                        CapitalsBinding.Resolution resolution) {
        if (!loaded) {
            return new NoopCapitalsBridge(false);
        }
        if (!enabled) {
            return new NoopCapitalsBridge(true);
        }
        // Installed and switched on, but nothing bound: this is a Capitals build the manifest no
        // longer understands, and a Noop bridge would report it as "not installed" and hide that.
        return ReflectiveCapitalsBridge.of(resolution.status() == CompatStatus.ABSENT
                ? CapitalsBinding.unavailable() : resolution);
    }

    /**
     * One line per capability, plus a count of the manifest members that did not bind.
     *
     * <p>The count rather than the list, because on a Capitals build that moved a class the list is
     * every member of it and would bury the eight capabilities that are still fine. The full list is
     * one debug line away in the log.
     */
    @Override
    public List<Component> diagnostics() {
        List<Component> lines = new ArrayList<>();
        boolean installed = bridge.status() != CompatStatus.ABSENT;
        lines.add(Component.translatable("mcaquests.command.compat.capitals.mod",
                Component.translatable(installed
                        ? "mcaquests.command.compat.capitals.mod.present"
                        : "mcaquests.command.compat.capitals.mod.absent")));
        for (CapitalsCapability capability : CapitalsCapability.values()) {
            lines.add(Component.translatable("mcaquests.command.compat.capitals.capability",
                    capability.id(), Component.translatable(bridge.has(capability)
                            ? "mcaquests.command.compat.capitals.ok"
                            : "mcaquests.command.compat.capitals.unavailable")));
        }
        lines.add(Component.translatable("mcaquests.command.compat.capitals.unresolved",
                bridge.unresolvedMembers().size()));
        return List.copyOf(lines);
    }
}
