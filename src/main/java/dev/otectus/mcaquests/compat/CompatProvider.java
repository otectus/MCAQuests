package dev.otectus.mcaquests.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * One optional mod, as the rest of MCA: Quests is allowed to see it.
 *
 * <p>Every existing integration already answers these questions — {@code TownsteadBridge#status()},
 * {@code McaBinding.Resolution}, {@code FtbqBridge#isAvailable()} — but each in its own vocabulary,
 * so nothing could ask "what is installed, and what does it give me?" once. This is that one
 * question, and the answer it returns is deliberately narrow: ids, booleans and {@link Component}s.
 *
 * <p><b>Only {@code java.*} and {@code net.minecraft.*} types may appear here.</b> An implementation
 * reaches its mod reflectively, exactly as {@code TownsteadBinding} does; the static-link tests scan
 * for anything else.
 *
 * <p>Every method must be total and cheap: these are called from offer filters, the quest log sync
 * and a command, where an exception costs a tick and a slow answer costs a frame. {@link #reprobe}
 * is the one place work is allowed, and it is called only from
 * {@link CompatRegistry#reprobeAll(String, RegistryAccess)}.
 */
public interface CompatProvider {

    /** Stable lowercase id, normally the mod id. Unique across the registry. */
    String id();

    /** What to call this mod in front of a player. A translation key, never a hardcoded name. */
    Component displayName();

    /**
     * The resource namespaces whose content belongs to this mod. Used to explain a missing id and to
     * decide that an unresolved target names optional content rather than a typo.
     */
    Set<String> namespaces();

    /** How much of this mod is reachable right now. */
    CompatStatus status();

    /** Everything this provider can answer for, present or not. Never null; may be empty. */
    List<CompatCapability> capabilities();

    /**
     * Re-decides {@link #status()} and {@link #capabilities()}.
     *
     * <p>Called on world load, on {@code /reload} and just before a server starts, because registry
     * contents are only answerable once registries have frozen and a datapack reload can change what
     * is mounted. {@code access} is the reload's {@link RegistryAccess} when one exists and
     * {@code null} otherwise; an implementation that needs dynamic registries must tolerate null by
     * leaving those capabilities as they were.
     *
     * <p>Must not throw. An adapter over a binding that is resolved once and cached implements this
     * as a documented no-op.
     */
    void reprobe(@Nullable RegistryAccess access);

    /** Extra lines for the status command — versions, flavours, unbound members. Empty by default. */
    default List<Component> diagnostics() {
        return List.of();
    }

    /** True when this provider declares {@code capabilityId} and it is present. */
    default boolean has(String capabilityId) {
        for (CompatCapability capability : capabilities()) {
            if (capability.id().equals(capabilityId)) {
                return capability.present();
            }
        }
        return false;
    }
}
