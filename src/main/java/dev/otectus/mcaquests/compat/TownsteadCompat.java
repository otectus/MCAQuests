package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.neoforged.fml.ModList;

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
 * dedicated servers on right-click. So the real implementation lives entirely under
 * {@code compat.townstead}, reached through the dotted string below only after {@link ModList}
 * confirms Townstead is present.
 *
 * <p>The class name is stored <em>dotted</em>. The JVM writes real class references in internal
 * (slash) form, so a dotted literal can never be mistaken for linkage — which is why
 * {@code NoTownsteadStaticLinkTest} needs no exemption for this file.
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

    private static boolean initialised;

    private TownsteadCompat() {
    }

    /**
     * Binds Townstead if it is present and enabled. Called once from mod setup, after Forge has
     * loaded every mod, so {@link ModList} is authoritative — and after MCA has been bound, because
     * the spirit capability needs an MCA village object that only {@code McaHandles} can produce.
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

        try {
            Class<?> implementation = Class.forName(IMPLEMENTATION);
            TownsteadBridge candidate =
                    (TownsteadBridge) implementation.getDeclaredConstructor().newInstance();
            TownsteadBridge.Holder.set(candidate);
            report(candidate);
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] Townstead is installed but the integration could not "
                    + "start; Townstead content stays ineligible and the rest of MCA: Quests is "
                    + "unaffected. Please report this with your Townstead version.", t);
        }
    }

    /** One line, chosen by outcome. Never more — this runs once, and nobody wants a wall of it. */
    private static void report(TownsteadBridge bridge) {
        String version = bridge.detectedVersion();
        String variant = bridge.variant().orElse("unknown");
        switch (bridge.status()) {
            case FULL -> McaQuests.LOGGER.info(
                    "[MCA: Quests] Townstead {} detected (MCA root: {}); {} capabilities bound. "
                            + "Needs, schedules, professions, skills, buildings and village spirit are "
                            + "now quest state.",
                    version, variant, bridge.capabilities().size());
            case PARTIAL -> McaQuests.LOGGER.warn(
                    "[MCA: Quests] Townstead {} detected (MCA root: {}) but only {} of {} capabilities "
                            + "bound. Content needing the rest stays ineligible. Run "
                            + "'/mcaquests compat townstead status' to see which, and report it with "
                            + "your Townstead version.",
                    version, variant, bridge.capabilities().size(), TownsteadCapability.values().length);
            case DISABLED -> McaQuests.LOGGER.warn(
                    "[MCA: Quests] Townstead {} is installed but none of its API could be bound, so the "
                            + "integration is disabled. This usually means an unsupported Townstead "
                            + "version. Run '/mcaquests compat townstead status' for details.",
                    version);
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
