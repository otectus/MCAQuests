package dev.otectus.mcaquests.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * MCA: Crime, expressed as a {@link CompatProvider} (1.7.1), so {@code /mcaquests compat} can say whether
 * the wanted-player refusal and the {@code crime_status} condition are live, and so content in the
 * {@code mcacrime} namespace (its bounty board quest) is attributed to the right mod.
 *
 * <p>Read-only over {@link CrimeBridge}; it never binds anything itself and names no Crime type.
 */
public final class CrimeCompatProvider implements CompatProvider {

    private static final String WANTED = "wanted";
    private static final String CUSTODY = "custody";

    @Override
    public String id() {
        return "mcacrime";
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.mcacrime.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("mcacrime");
    }

    @Override
    public CompatStatus status() {
        if (!ModList.get().isLoaded("mcacrime")) {
            return CompatStatus.ABSENT;
        }
        return CrimeBridge.isAvailable() ? CompatStatus.FULL : CompatStatus.DISABLED;
    }

    @Override
    public List<CompatCapability> capabilities() {
        boolean live = CrimeBridge.isAvailable();
        return List.of(new CompatCapability(WANTED, live, CapabilityEvidence.ADAPTER_CONFIRMED),
                new CompatCapability(CUSTODY, live, CapabilityEvidence.ADAPTER_CONFIRMED));
    }

    /** A no-op: the bridge binds once at common setup, and the set of installed mods cannot change. */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
    }

    @Override
    public List<Component> diagnostics() {
        return List.of(Component.literal(CrimeBridge.status()));
    }
}
