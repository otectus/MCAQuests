package dev.otectus.mcaquests.compat.mca;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What became of the MCA Gift hook, recorded by the mixin config plugin and read by everything that
 * needs to know whether Gift can pay a quest delivery on this installation.
 *
 * <p>The hook is shipped once per MCA package root, so a normal, healthy game reports <b>one applied
 * variant and three skipped ones</b> — the roots MCA is not using simply have no class to read. That
 * is why this is not a single state: a later "skipped" must never be able to overwrite an earlier
 * "applied", which is exactly what a one-slot answer would do.
 *
 * <p>The distinction that matters to a player is the third case: MCA is installed and <em>no</em>
 * variant applied. Then the Deliver button in the quest menu is the only route, the Gift hint must not
 * be shown, and the reason belongs in the log rather than in a silent fallback that hands their quest
 * item over as an ordinary present.
 *
 * <p><b>Plain Java, deliberately.</b> Nothing here imports Minecraft, Forge or Mixin: it is written
 * during mixin bootstrap, long before the game exists, and a class that pulled anything else in at
 * that point would either fail to load or drag half the mod onto the transformer's class path. The
 * once-per-session log line is therefore emitted by {@link McaBinding#logGiftHookOnce()}, where a
 * logger is a normal thing to have.
 *
 * <p>That line is deliberately <b>not</b> printed during common setup: a mixin applies when its target
 * class is first loaded, which here is when somebody talks to a villager, so setup is too early to
 * know the answer. The delivery layer calls it instead, at the first moment the answer decides
 * something — see {@code DeliveryService#giftBridgeAvailable()}.
 */
public final class McaGiftHookProbe {

    /** How far one variant of the hook got. */
    public enum State {

        /** Nothing has reported for this target — the usual answer before mixins run. */
        UNKNOWN,

        /** The hook is in MCA's command handler and was verified after transformation. */
        APPLIED,

        /** Something went wrong while deciding or applying it. */
        FAILED,

        /** Deliberately not applied: this root is not the one MCA is using, or its shape is not ours. */
        SKIPPED
    }

    /** One target class's outcome, for the diagnostic line. */
    public record Outcome(State state, String reason) {
    }

    private static final Map<String, Outcome> OUTCOMES = new LinkedHashMap<>();
    private static volatile boolean applied;
    private static volatile boolean mcaPresent;

    private McaGiftHookProbe() {
    }

    /** Records that the hook is in place for {@code target}. Sticky: one success is enough. */
    public static synchronized void applied(String target) {
        OUTCOMES.put(key(target), new Outcome(State.APPLIED, ""));
        applied = true;
    }

    /** Records that the hook could not be applied to {@code target}, and why. */
    public static synchronized void failed(String target, String why) {
        OUTCOMES.put(key(target), new Outcome(State.FAILED, why == null ? "" : why));
    }

    /** Records that the hook was correctly not applied to {@code target}, and why not. */
    public static synchronized void skipped(String target, String why) {
        OUTCOMES.put(key(target), new Outcome(State.SKIPPED, why == null ? "" : why));
    }

    /**
     * Records that the hook has actually run.
     *
     * <p>Ground truth, and deliberately stronger than the plugin's byte check: the hook executing is
     * proof that it applied, whatever the verification thought. It exists so that a future change to
     * how Mixin emits an injected call can, at worst, cost a log line — never the capability flag the
     * interface reads.
     */
    public static void observed() {
        applied = true;
        mcaPresent = true;
    }

    /** Records whether Forge has a mod file for MCA at all, as the plugin saw it. */
    public static void mcaPresent(boolean present) {
        mcaPresent = present;
    }

    /**
     * True when some variant of the hook is in place.
     *
     * <p>The one question the delivery service asks. Never inferred from MCA being installed: the
     * whole point of the per-variant record is that those are different facts.
     */
    public static boolean applied() {
        return applied;
    }

    /** True when the plugin saw MCA installed, whether or not any variant applied. */
    public static boolean mcaPresent() {
        return mcaPresent;
    }

    /** True for the one state worth warning about: MCA is here and the bridge is not. */
    public static boolean unhookedWithMcaPresent() {
        return mcaPresent && !applied;
    }

    /** The outcome recorded for one target class. */
    public static synchronized Outcome outcome(String target) {
        return OUTCOMES.getOrDefault(key(target), new Outcome(State.UNKNOWN, ""));
    }

    /** A one-line human-readable summary, for the session log line and {@code /mcaquests debug}. */
    public static synchronized String describe() {
        StringBuilder text = new StringBuilder("giftHook applied=").append(applied)
                .append(" mcaPresent=").append(mcaPresent);
        OUTCOMES.forEach((target, outcome) -> {
            text.append(' ').append(shortName(target)).append('=').append(outcome.state());
            if (!outcome.reason().isEmpty()) {
                text.append('(').append(outcome.reason()).append(')');
            }
        });
        return text.toString();
    }

    /** Test seam: forget everything reported so far. Production never resets. */
    public static synchronized void resetForTest() {
        OUTCOMES.clear();
        applied = false;
        mcaPresent = false;
    }

    /**
     * Targets arrive in whichever form the caller holds — Mixin uses both dotted and internal names —
     * so they are normalised to the dotted one before being used as a key. Dotted also keeps every
     * string in this class away from the internal form {@code NoMcaStaticLinkTest} scans for.
     */
    private static String key(String target) {
        return target == null ? "" : target.replace('/', '.');
    }

    /** Just the package root, which is the only part that differs between the variants. */
    private static String shortName(String target) {
        int entity = target.indexOf(".entity.");
        return entity > 0 ? target.substring(0, entity) : target;
    }
}
