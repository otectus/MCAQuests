package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.bountiful.BountifulCompat;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one place that knows which optional mods are installed and what they give us.
 *
 * <p>Before this existed each integration answered for itself in its own vocabulary, so there was no
 * way to ask "is this content available?" without naming the mod that provides it — which is exactly
 * what a quest condition, an unresolved target and a status command all need to do generically.
 *
 * <p>A singleton rather than a static holder because {@link #get()} reads better at the call sites
 * that matter ({@code CompatRegistry.get().has("townstead", "read_needs")}) and because the tests
 * want an instance to reason about. Registration order is preserved so {@code compat status} prints
 * the same list every time.
 *
 * <p><b>Thread-safety.</b> Providers are registered once during mod construction and never removed;
 * {@link #reprobeAll} runs on the server thread. Reads are lock-free over a map that is only ever
 * replaced whole.
 */
public final class CompatRegistry {

    private static final CompatRegistry INSTANCE = new CompatRegistry();

    private volatile Map<String, CompatProvider> providers = Map.of();
    private volatile int reprobeCount;

    private CompatRegistry() {
    }

    public static CompatRegistry get() {
        return INSTANCE;
    }

    /**
     * Registers the built-in adapters. Called from the {@code McaQuests} constructor, before anything
     * can read the registry — a provider that is not registered by then would silently answer "no"
     * to every question asked of it.
     */
    public static void bootstrap() {
        CompatRegistry registry = get();
        registry.register(new TownsteadCompatProvider());
        registry.register(new McaCompatProvider());
        registry.register(new FtbqCompatProvider());
        registry.register(new IceAndFireCompat());
        registry.register(new BountifulCompat());
    }

    /** Adds a provider, replacing any earlier one with the same {@link CompatProvider#id()}. */
    public synchronized void register(CompatProvider provider) {
        Map<String, CompatProvider> next = new LinkedHashMap<>(providers);
        next.put(provider.id(), provider);
        providers = Map.copyOf(next);
    }

    /** Every registered provider, in registration order. */
    public Collection<CompatProvider> providers() {
        return providers.values();
    }

    public Optional<CompatProvider> provider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /** True when {@code providerId} is registered and declares {@code capabilityId} as present. */
    public boolean has(String providerId, String capabilityId) {
        CompatProvider provider = providers.get(providerId);
        return provider != null && provider.has(capabilityId);
    }

    /** The provider that owns a resource namespace, if one claims it. */
    public Optional<CompatProvider> forNamespace(String namespace) {
        for (CompatProvider provider : providers.values()) {
            if (provider.namespaces().contains(namespace)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /**
     * Why an id cannot be resolved, in words a player can act on: the mod's own name when we know
     * which mod it belongs to, and the bare namespace when we do not. Never the raw id alone — "%s is
     * not available" reading "iceandfire:hydra is not available" tells a player nothing they can do.
     */
    public Component describeMissing(ResourceLocation id) {
        Object subject = forNamespace(id.getNamespace())
                .<Object>map(CompatProvider::displayName)
                .orElse(id.getNamespace());
        return Component.translatable("mcaquests.objective.unavailable.compat", subject);
    }

    /**
     * Re-probes every provider. {@code cause} is logged at debug only — this runs on every world load
     * and every {@code /reload}, and a line per reload is noise on a server that is working.
     *
     * <p>A provider that throws is reported once and skipped: a broken adapter must not take the
     * reload with it.
     */
    public void reprobeAll(String cause, @Nullable RegistryAccess access) {
        for (CompatProvider provider : providers.values()) {
            try {
                provider.reprobe(access);
            } catch (Throwable t) {
                McaQuests.LOGGER.warn("[MCA: Quests] Compat provider '{}' failed to re-probe ({}); its "
                        + "previous answers are kept.", provider.id(), cause, t);
            }
        }
        reprobeCount++;
        McaQuests.LOGGER.debug("[MCA: Quests] Re-probed {} compat provider(s) after {}.",
                providers.size(), cause);
    }

    /** How many times {@link #reprobeAll} has run. Diagnostics and tests only. */
    public int reprobeCount() {
        return reprobeCount;
    }

    /** Test seam: drop every registered provider. Production never removes one. */
    public synchronized void clearForTest() {
        providers = Map.of();
        reprobeCount = 0;
    }

    /** Every provider whose status is worth mentioning, for a one-line startup summary. */
    public List<CompatProvider> installed() {
        return providers.values().stream()
                .filter(provider -> provider.status() != CompatStatus.ABSENT)
                .toList();
    }
}
