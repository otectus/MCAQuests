package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The bridge used when Bountiful is not installed, or when {@code compat.bountiful.mode} is
 * {@code OFF}.
 *
 * <p>It is a whole class rather than a flag because the most common installation by far is the one
 * without Bountiful, and that path should hold nothing at all: no handles, no listeners, no cached
 * probe, nothing that could throw. Every capability answers "no", which is what makes a gated quest
 * simply never offered and a quest already accepted pause instead of break.
 *
 * <p>{@link #status()} distinguishes the two reasons, because a server owner who switched the
 * integration off and a server owner who forgot to install the mod need different answers from
 * {@code /mcaquests compat bountiful status}.
 */
public final class NoopBountifulBridge implements BountifulBridge {

    private final CompatStatus status;

    /**
     * @param disabled true when Bountiful is installed but the integration is switched off, which
     *                 reports {@link CompatStatus#DISABLED} rather than {@link CompatStatus#ABSENT}
     */
    public NoopBountifulBridge(boolean disabled) {
        this.status = disabled ? CompatStatus.DISABLED : CompatStatus.ABSENT;
    }

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.bountiful.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(MOD_ID);
    }

    @Override
    public CompatStatus status() {
        return status;
    }

    /**
     * Every capability, declared and absent.
     *
     * <p>Declaring them rather than returning nothing is deliberate: {@code compat status} then lists
     * the same rows on every installation, so "the board capability is missing" and "this build does
     * not have a board capability at all" cannot be confused for one another.
     */
    @Override
    public List<CompatCapability> capabilities() {
        List<CompatCapability> capabilities = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            capabilities.add(new CompatCapability(capability.id(), false,
                    dev.otectus.mcaquests.compat.CapabilityEvidence.REGISTRY_CONFIRMED));
        }
        return List.copyOf(capabilities);
    }

    @Override
    public void reprobe(@Nullable RegistryAccess access) {
        // Nothing to re-probe. Which bridge is in use is decided by BountifulCompat, which builds a
        // fresh one on every probe; this object never changes its mind about anything.
    }

    @Override
    public boolean has(Capability capability) {
        return false;
    }

    @Override
    public Optional<BountySnapshot> inspect(ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public void addCompletionListener(BountifulCompletionListener listener) {
        // Kept by BountifulCompat rather than dropped here. Bridges are rebuilt on every re-probe and
        // listeners are registered once at start-up, so a list owned by a bridge would be thrown away
        // the first time a /reload happened -- and the listener would go quiet with nothing to show
        // for it. It is still never called while this bridge is in use, which is the whole point of
        // BountifulBridge#addCompletionListener accepting one at all.
        BountifulCompat.addCompletionListener(listener);
    }
}
