package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import net.neoforged.fml.ModList;

/**
 * The optional-classloading seam for MCA: Reputation (spec §29.1), built to exactly the discipline
 * {@link FtbqBridge} and MCA: Conversations' {@code QuestsBridge} already use in this suite.
 *
 * <h2>The rule this class exists to enforce</h2>
 *
 * <p><b>Nothing in this file, or in anything it can reach without the mod-present check, may name a
 * {@code mcareputation} type.</b> Java resolves a class's references lazily, but "lazily" is not
 * "never" — a field type, a method signature, an annotation, a static initialiser, or an event
 * subscriber that mentions a missing class will throw {@code NoClassDefFoundError} the moment
 * something touches it, and where that lands is a detail of the JVM rather than something a mod can
 * rely on. So the real implementation lives entirely under {@code compat.reputation}, and the only
 * reference to it is the reflective-in-effect string below, resolved after {@link ModList} confirms
 * the mod is present.
 *
 * <p>Initialisation is wrapped in {@code catch (Throwable)}. If MCA: Reputation is installed but its
 * API has moved — a different major version, a partial jar — the integration disables itself with one
 * clear ERROR and Quests carries on with its own store. §35.1 requires exactly that: a bridge failure
 * is one log line, never a crash.
 */
public final class ReputationBridge {

    /** The Reputation API generation this build was written against (see {@code McaReputationApi}). */
    public static final int REQUIRED_API_VERSION = 1;

    private static ReputationBackend backend = new LegacyReputationBackend();
    private static boolean initialised;

    private ReputationBridge() {
    }

    /**
     * Chooses the backend. Called once from mod setup, after Forge has loaded every mod, so
     * {@link ModList} is authoritative by this point.
     */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!ModList.get().isLoaded("mcareputation")) {
            McaQuests.LOGGER.info("[MCA: Quests] MCA: Reputation is not installed; using the built-in "
                    + "per-player village standing store.");
            return;
        }
        try {
            // Class.forName rather than a direct reference: naming the class here would put it in this
            // class's constant pool and defeat the whole seam.
            Class<?> implementation = Class.forName(
                    "dev.otectus.mcaquests.compat.reputation.CanonicalReputationBackend");
            ReputationBackend candidate =
                    (ReputationBackend) implementation.getDeclaredConstructor().newInstance();
            if (!candidate.isCanonical()) {
                McaQuests.LOGGER.error("[MCA: Quests] MCA: Reputation is present but reported an "
                        + "incompatible API version (this build needs v{}); the integration is disabled "
                        + "and Quests will use its own store.", REQUIRED_API_VERSION);
                return;
            }
            backend = candidate;
            // Registered only now, for the same reason the backend is: this class names
            // mcareputation event types and must never be loaded without the mod present.
            Class.forName("dev.otectus.mcaquests.compat.reputation.QuestsReputationEvents")
                    .getMethod("register").invoke(null);
            McaQuests.LOGGER.info("[MCA: Quests] MCA: Reputation detected; village standing, tiers, and "
                    + "titles now delegate to it, and Quests keeps a mirrored fallback copy.");
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] MCA: Reputation is installed but the integration could "
                    + "not start; falling back to the built-in store. Quests remains fully playable.", t);
        }
    }

    /** The active backend. Never null, and never changes after {@link #init}. */
    public static ReputationBackend backend() {
        return backend;
    }

    /** True when MCA: Reputation owns the canonical state. */
    public static boolean isCanonical() {
        return backend.isCanonical();
    }

    /**
     * Test seam: force a backend. Production code calls {@link #init} exactly once from mod setup;
     * the regression suite uses this to exercise both the present and absent cases in one JVM.
     */
    public static synchronized void setBackendForTest(ReputationBackend replacement) {
        backend = replacement == null ? new LegacyReputationBackend() : replacement;
        initialised = true;
    }

    /** Test seam: restore the built-in fallback. */
    public static synchronized void resetForTest() {
        backend = new LegacyReputationBackend();
        initialised = false;
    }
}
