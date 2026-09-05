package dev.otectus.mcaquests.compat.iceandfire;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ice &amp; Fire, expressed as a {@link CompatProvider}.
 *
 * <p>The integration is <b>registry-only</b>: nothing here calls into either build of the mod, and no
 * Ice &amp; Fire type is named anywhere in this package — {@link IceAndFireFlavor}'s two class names
 * are dotted string literals for {@code Class.forName}, which is what
 * {@code NoIceAndFireStaticLinkTest} relies on. Everything MCA: Quests needs to know is answerable
 * from the vanilla registries, so there is no binding to resolve and nothing to go stale between
 * versions.
 *
 * <p>Unlike {@code TownsteadCompatProvider}, this one really does re-probe. Structures live in a
 * dynamic registry that only exists once a world is loaded, so the first probe during mod setup can
 * never answer for them; {@link #reprobe} run with a {@link RegistryAccess} fills that in, and
 * {@code /reload} gets a fresh answer for free.
 */
public final class IceAndFireCompat implements CompatProvider {

    private volatile IceAndFireFlavor flavor = IceAndFireFlavor.NONE;
    private volatile CompatStatus status = CompatStatus.ABSENT;
    private volatile Map<String, CompatCapability> capabilities = Map.of();
    private volatile String detectedVersion = "";
    private volatile boolean ambiguityLogged;

    @Override
    public String id() {
        return IceAndFireRegistryManifest.MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.iceandfire.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(IceAndFireRegistryManifest.MOD_ID);
    }

    @Override
    public CompatStatus status() {
        return status;
    }

    @Override
    public List<CompatCapability> capabilities() {
        return List.copyOf(capabilities.values());
    }

    /** Which build answered the class probe. Diagnostics only; never gates registry-backed content. */
    public IceAndFireFlavor flavor() {
        return flavor;
    }

    /**
     * The version string Forge reports for the mod container, or the empty string when nothing is
     * installed. Diagnostics only, and deliberately never persisted: a saved version number would
     * become a claim about a world that outlives the install it was written on.
     */
    public String detectedVersion() {
        return detectedVersion;
    }

    /**
     * Whether MCA: Quests' own Ice &amp; Fire quest pack may be mounted.
     *
     * <p>Separate from {@code compat.iceandfire.enabled} because the two answer different questions: a
     * server that writes its own dragon quests wants the capability probe and the
     * {@code compat_capability} condition, but not our content. Read by the conditional pack
     * registrar; it never affects what {@link #capabilities()} reports.
     */
    public boolean builtinContentEnabled() {
        return McaQuestsConfig.COMMON.iceAndFireEnableBuiltinContent.get();
    }

    @Override
    public void reprobe(@Nullable RegistryAccess access) {
        boolean loaded = ModList.get().isLoaded(IceAndFireRegistryManifest.MOD_ID);
        if (!loaded) {
            flavor = IceAndFireFlavor.NONE;
            detectedVersion = "";
            capabilities = IceAndFireCapabilities.probe(IceAndFireFlavor.NONE, false,
                    unused -> false, unused -> false, null);
            status = CompatStatus.ABSENT;
            return;
        }

        detectedVersion = ModList.get().getModContainerById(IceAndFireRegistryManifest.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        flavor = IceAndFireFlavor.detect(IceAndFireCompat::classPresent);
        capabilities = IceAndFireCapabilities.probe(flavor, true,
                ForgeRegistries.ENTITY_TYPES::containsKey,
                ForgeRegistries.ITEMS::containsKey,
                structurePredicate(access));

        if (!McaQuestsConfig.COMMON.iceAndFireEnabled.get()) {
            status = CompatStatus.DISABLED;
            return;
        }
        if (flavor == IceAndFireFlavor.AMBIGUOUS) {
            if (!ambiguityLogged) {
                ambiguityLogged = true;
                McaQuests.LOGGER.warn("[MCA: Quests] Both Ice & Fire implementations detected; using "
                        + "registry-only integration.");
            }
            status = CompatStatus.AMBIGUOUS;
            return;
        }
        status = has(IceAndFireCapabilities.CORE) ? CompatStatus.FULL : CompatStatus.PARTIAL;
    }

    @Override
    public List<Component> diagnostics() {
        return IceAndFireDiagnostics.lines(this);
    }

    /**
     * Structures are a dynamic registry, so there is nothing to ask outside a loaded world. Returning
     * {@code null} rather than a predicate that always says "no" is what lets the capability report
     * "not answerable now" instead of "this world has none".
     */
    @Nullable
    private static java.util.function.Predicate<ResourceLocation> structurePredicate(
            @Nullable RegistryAccess access) {
        if (access == null) {
            return null;
        }
        return id -> {
            try {
                return access.registryOrThrow(Registries.STRUCTURE).containsKey(id);
            } catch (Throwable t) {
                return false;
            }
        };
    }

    /**
     * {@code initialize = false} deliberately: this asks whether a class exists, and running an entry
     * point's static initialiser to find out would be both slow and, on a half-loaded mod, fatal.
     * Every {@link Throwable} is an answer of "no" — a linkage error from a mod that is present but
     * broken must not take a probe down with it.
     */
    private static boolean classPresent(String binaryName) {
        try {
            Class.forName(binaryName, false, IceAndFireCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
