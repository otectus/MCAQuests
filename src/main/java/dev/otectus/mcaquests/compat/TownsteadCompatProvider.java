package dev.otectus.mcaquests.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Townstead, expressed as a {@link CompatProvider}.
 *
 * <p>A read-only view over {@link TownsteadBridge} and nothing else: it binds nothing, caches
 * nothing and changes no Townstead behaviour. Its whole job is to let {@code /mcaquests compat
 * status} and {@code mcaquests:compat_capability} talk about Townstead in the same words they use
 * for every other optional mod, while {@code mcaquests:townstead_available} — which datapacks have
 * been using since 1.4.0 — keeps working unchanged.
 *
 * <p>Capability ids are the {@link TownsteadCapability} names lowercased, so
 * {@code {"type": "mcaquests:compat_capability", "provider": "townstead", "capability": "read_needs"}}
 * asks the same question as the Townstead-specific condition.
 */
public final class TownsteadCompatProvider implements CompatProvider {

    @Override
    public String id() {
        return "townstead";
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.townstead.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("townstead");
    }

    @Override
    public CompatStatus status() {
        return switch (TownsteadBridge.Holder.get().status()) {
            case ABSENT -> CompatStatus.ABSENT;
            case DISABLED -> CompatStatus.DISABLED;
            case PARTIAL -> CompatStatus.PARTIAL;
            case FULL -> CompatStatus.FULL;
        };
    }

    @Override
    public List<CompatCapability> capabilities() {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        List<CompatCapability> out = new ArrayList<>(TownsteadCapability.values().length);
        for (TownsteadCapability capability : TownsteadCapability.values()) {
            out.add(new CompatCapability(capability.name().toLowerCase(Locale.ROOT),
                    bridge.has(capability), CapabilityEvidence.ADAPTER_CONFIRMED));
        }
        return List.copyOf(out);
    }

    /**
     * Deliberately a no-op. {@code TownsteadBinding} resolves once, lazily, against the classloader
     * and is cached for the life of the JVM — a mod cannot be added or removed while the game runs,
     * so there is nothing a second probe could learn. Re-probing would only re-enumerate every method
     * of every Townstead class, which is the expensive half of binding.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
    }

    @Override
    public List<Component> diagnostics() {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (bridge.status() == TownsteadStatus.ABSENT) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable("mcaquests.command.compat.status.version",
                bridge.detectedVersion(), bridge.variant().orElse("?")));
        List<String> unresolved = bridge.unresolvedMembers();
        if (!unresolved.isEmpty()) {
            lines.add(Component.translatable("mcaquests.command.compat.status.unresolved",
                    unresolved.size(), String.join(", ", unresolved)));
        }
        return List.copyOf(lines);
    }
}
