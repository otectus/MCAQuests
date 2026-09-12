package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraftforge.fml.ModList;

/**
 * The optional-classloading seam for Townstead (Townstead spec §3.2), built to the discipline
 * {@link FtbqBridge} and {@link ReputationBridge} already use in this suite.
 *
 * <h2>The rule this class exists to enforce</h2>
 *
 * <p><b>Nothing in this file, or in anything it can reach without the mod-present check, may name a
 * {@code com.aetherianartificer.townstead} type.</b> Java resolves references lazily, but "lazily"
 * is not "never": a field type, a method signature or a static initialiser mentioning a missing
 * class throws {@code NoClassDefFoundError} the moment something touches it, and MCA: Quests has
 * already shipped that exact bug once — a stale MCA import inside an entity-interact handler killed
 * dedicated servers on right-click. So the real implementations live entirely under
 * {@code compat.townstead}, reached through the dotted strings below only after {@link ModList}
 * confirms Townstead is present.
 *
 * <p>The class names are stored <em>dotted</em>. The JVM writes real class references in internal
 * (slash) form, so a dotted literal can never be mistaken for linkage — which is why
 * {@code NoTownsteadStaticLinkTest} needs no exemption for this file.
 *
 * <h2>Two bridges, chosen by what the installed Townstead ships</h2>
 *
 * <p>Townstead 0.8 publishes a frozen, versioned API, {@code com.aetherianartificer.townstead.api.v1}.
 * When that package is present the <b>typed</b> bridge ({@code compat.townstead.v1}) is the only
 * acceptable binding: its writes carry this mod's source id, which a server can refuse in
 * Townstead's config, and its reads are contract rather than internals. If the API is present but
 * the typed adapter cannot be used — this build was made without it, the API generation is not
 * the one the adapter was written for, or its start-up threw — the integration is
 * {@link TownsteadStatus#DISABLED} with the reason, <em>not</em> handed to reflection: the by-name
 * binding was written against 0.7.x internals and would bypass the API's write policy. Older
 * Townstead builds, which have no {@code api.v1}, keep the <b>reflective</b> bridge exactly as
 * every release before 1.7 bound them.
 *
 * <h2>Logging</h2>
 *
 * <p>Spec §3.5: bind once, one INFO on success, one WARN when degraded, and <b>nothing at all when
 * Townstead is simply absent</b> — that is the normal case for most installs and it is not news.
 * Absence is recorded at DEBUG so {@code /mcaquests compat townstead status} still has something to
 * say when someone goes looking.
 */
public final class TownsteadCompat {

    private static final String MOD_ID = "townstead";

    /** Dotted on purpose — see the class javadoc. */
    private static final String IMPLEMENTATION =
            "dev.otectus.mcaquests.compat.townstead.ReflectiveTownsteadBridge";

    /**
     * The typed bridge over Townstead's frozen {@code api.v1}, and the API entry point whose presence
     * says the installed Townstead ships it. Both dotted for the same reason as above; the probe is
     * Townstead's own interface, which names no MCA type, and is looked up without initialising it.
     */
    private static final String API_IMPLEMENTATION =
            "dev.otectus.mcaquests.compat.townstead.v1.ApiTownsteadBridge";
    private static final String API_PROBE = "com.aetherianartificer.townstead.api.v1.TownsteadApiV1";

    /** Which bridge {@link #init()} will try, decided from what is on the classpath. */
    public enum Binding {
        /** Townstead ships {@code api.v1} and this build carries the typed adapter. */
        TYPED,
        /** Townstead predates {@code api.v1}: the by-name binding, as before. */
        REFLECTIVE,
        /** Townstead ships {@code api.v1} but this build has no typed adapter: bind nothing, say why. */
        DISABLED_NO_ADAPTER
    }

    private static boolean initialised;

    private TownsteadCompat() {
    }

    /**
     * The decision table, kept pure so it can be tested without a classloader: reflection is only
     * ever used on a Townstead that has no public API, and the API is only ever used through the
     * typed adapter.
     */
    static Binding chooseBinding(boolean apiPresent, boolean adapterPresent) {
        if (!apiPresent) {
            return Binding.REFLECTIVE;
        }
        return adapterPresent ? Binding.TYPED : Binding.DISABLED_NO_ADAPTER;
    }

    /**
     * Binds Townstead if it is present and enabled. Called once from mod setup, after Forge has
     * loaded every mod, so {@link ModList} is authoritative — and after MCA has been bound, because
     * the reflective spirit capability needs an MCA village object that only {@code McaHandles} can
     * produce.
     */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        if (!ModList.get().isLoaded(MOD_ID)) {
            McaQuests.LOGGER.debug("[MCA: Quests] Townstead is not installed; its content stays "
                    + "ineligible and no Townstead state is queried.");
            return;
        }
        if (!McaQuestsConfig.COMMON.townsteadEnabled.get()) {
            McaQuests.LOGGER.info("[MCA: Quests] Townstead is installed but the integration is "
                    + "switched off (compat.townstead.enabled=false); its content stays ineligible.");
            return;
        }

        Binding binding = chooseBinding(classPresent(API_PROBE), classPresent(API_IMPLEMENTATION));
        TownsteadBridge candidate = null;
        try {
            switch (binding) {
                case TYPED -> candidate = instantiate(API_IMPLEMENTATION);
                case REFLECTIVE -> candidate = instantiate(IMPLEMENTATION);
                case DISABLED_NO_ADAPTER -> candidate = new DisabledTownsteadBridge(installedVersion(),
                        "this Townstead ships api.v1 but this MCA: Quests build was made without the typed "
                                + "adapter (reflective-only build); install a release build");
            }
            candidate.onBound();
            TownsteadBridge.Holder.set(candidate);
            report(candidate);
        } catch (Throwable t) {
            if (candidate != null) {
                try {
                    candidate.onUnbound();
                } catch (Throwable ignored) {
                    // Releasing a half-registered bridge is best effort; the disabled one below wins.
                }
            }
            String reason = binding == Binding.TYPED
                    ? "the typed adapter over Townstead api.v1 could not start: " + t
                    : "the integration could not start: " + t;
            TownsteadBridge.Holder.set(new DisabledTownsteadBridge(installedVersion(), reason));
            McaQuests.LOGGER.error("[MCA: Quests] Townstead is installed but the integration could not "
                    + "start ({}); Townstead content stays ineligible and the rest of MCA: Quests is "
                    + "unaffected. Please report this with your Townstead version.", binding, t);
        }
    }

    private static TownsteadBridge instantiate(String className) throws ReflectiveOperationException {
        Class<?> implementation = Class.forName(className);
        return (TownsteadBridge) implementation.getDeclaredConstructor().newInstance();
    }

    /** Loads without initialising: a static initialiser must not run just to answer "is it there". */
    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, TownsteadCompat.class.getClassLoader());
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

    private static String installedVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    /** One line, chosen by outcome. Never more — this runs once, and nobody wants a wall of it. */
    private static void report(TownsteadBridge bridge) {
        String version = bridge.detectedVersion();
        String binding = bridge.bindingPath();
        String variant = bridge.variant().orElse("unknown");
        switch (bridge.status()) {
            case FULL -> McaQuests.LOGGER.info(
                    "[MCA: Quests] Townstead {} detected (binding: {}, variant: {}); {} capabilities bound. "
                            + "Needs, schedules, professions, skills, buildings and village spirit are "
                            + "now quest state.",
                    version, binding, variant, bridge.capabilities().size());
            case PARTIAL -> McaQuests.LOGGER.warn(
                    "[MCA: Quests] Townstead {} detected (binding: {}, variant: {}) but only {} of {} "
                            + "capabilities bound. Content needing the rest stays ineligible. Run "
                            + "'/mcaquests compat townstead status' to see which, and report it with "
                            + "your Townstead version.",
                    version, binding, variant, bridge.capabilities().size(), TownsteadCapability.values().length);
            case DISABLED -> McaQuests.LOGGER.warn(
                    "[MCA: Quests] Townstead {} is installed but the integration is disabled ({}). "
                            + "Townstead content stays ineligible. Run '/mcaquests compat townstead status' "
                            + "for details.",
                    version, binding);
            case ABSENT -> McaQuests.LOGGER.debug(
                    "[MCA: Quests] Townstead reported itself absent after binding.");
        }
    }

    /** Test seam: force a bridge. Production calls {@link #init()} exactly once from mod setup. */
    public static synchronized void setBridgeForTest(TownsteadBridge replacement) {
        TownsteadBridge.Holder.set(replacement);
        initialised = true;
    }

    /** Test seam: restore the absent-mod default. */
    public static synchronized void resetForTest() {
        TownsteadBridge.Holder.set(null);
        initialised = false;
    }
}
