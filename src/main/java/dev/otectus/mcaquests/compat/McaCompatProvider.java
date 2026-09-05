package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.compat.mca.McaBinding;
import dev.otectus.mcaquests.compat.mca.McaHandles;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MCA Reborn, expressed as a {@link CompatProvider}.
 *
 * <p>MCA is mandatory at runtime rather than optional, so this exists for the status command and for
 * completeness of {@link CompatRegistry#forNamespace}, not as a gate anything should hang content on
 * — a world without MCA has no quest givers at all, which no capability check would improve.
 *
 * <p>Read-only over {@link McaHandles#resolution()}; it never binds anything itself.
 */
public final class McaCompatProvider implements CompatProvider {

    /** The one capability worth naming: MCA bound well enough for villagers to be readable. */
    private static final String VILLAGERS = "villagers";

    @Override
    public String id() {
        return "mca";
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.mca.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("mca");
    }

    @Override
    public CompatStatus status() {
        return switch (McaHandles.resolution().status()) {
            case ABSENT -> CompatStatus.ABSENT;
            case UNBINDABLE -> CompatStatus.DISABLED;
            case PARTIAL -> CompatStatus.PARTIAL;
            case BOUND -> CompatStatus.FULL;
        };
    }

    @Override
    public List<CompatCapability> capabilities() {
        return List.of(new CompatCapability(VILLAGERS, McaHandles.available(),
                CapabilityEvidence.ADAPTER_CONFIRMED));
    }

    /**
     * Deliberately a no-op, for the same reason as the Townstead adapter's: {@link McaBinding}
     * resolves once against the classloader and caches, and the set of installed mods cannot change
     * while the game runs.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
    }

    @Override
    public List<Component> diagnostics() {
        McaBinding.Resolution resolution = McaHandles.resolution();
        List<Component> lines = new ArrayList<>(2);
        String root = resolution.root();
        lines.add(Component.translatable("mcaquests.command.compat.status.version",
                "-", root == null ? "?" : root));
        List<String> unresolved = resolution.unresolvedRequired();
        if (!unresolved.isEmpty()) {
            lines.add(Component.translatable("mcaquests.command.compat.status.unresolved",
                    unresolved.size(), String.join(", ", unresolved)));
        }
        return List.copyOf(lines);
    }
}
