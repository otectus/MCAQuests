package dev.otectus.mcaquests.compat.bountiful;

/**
 * What became of the Bountiful cash-in hook, recorded by the mixin config plugin and read by
 * everything that wants to know whether bounty completion is actually observable.
 *
 * <p>This is the answer to the one question the byte probe cannot give. {@code BountifulBinding}
 * reads the mod file and says the hook <em>can</em> go in; only the mixin itself, after Mixin has
 * transformed the class, knows that it <em>did</em>. A quest that silently never advances is the
 * failure this integration is most likely to produce and the hardest one to report, so the outcome is
 * kept and printed rather than inferred.
 *
 * <p><b>Plain Java, deliberately.</b> Nothing here imports Minecraft, Forge or Mixin: it is written
 * during mixin bootstrap, long before the game exists, and a class that pulled anything else in at
 * that point would either fail to load or drag half the mod onto the transformer's class path.
 *
 * <p>Static state because there is exactly one hook and exactly one answer. {@code volatile} because
 * the plugin writes it on the transformer thread and the status command reads it on the server one.
 */
public final class BountifulHookProbe {

    /** How far the hook got. */
    public enum State {

        /** Nothing has reported yet — the usual answer before the class is first loaded. */
        UNKNOWN,

        /** The hook is in Bountiful's method; completions will be seen. */
        APPLIED,

        /** Something went wrong while deciding or applying it; {@link #reason()} says what. */
        FAILED,

        /** Deliberately not applied: Bountiful is absent, or its method is not the one we can hook. */
        SKIPPED
    }

    private static volatile State state = State.UNKNOWN;
    private static volatile String reason = "";

    private BountifulHookProbe() {
    }

    /** Records that the hook is in place. */
    public static void applied() {
        state = State.APPLIED;
        reason = "";
    }

    /** Records that the hook could not be applied, and why. */
    public static void fail(String why) {
        state = State.FAILED;
        reason = why == null ? "" : why;
    }

    /** Records that the hook was correctly not applied, and why not. */
    public static void skipped(String why) {
        state = State.SKIPPED;
        reason = why == null ? "" : why;
    }

    public static State state() {
        return state;
    }

    /** The explanation behind a non-{@link State#APPLIED} state; empty when there is none. */
    public static String reason() {
        return reason;
    }

    /** Test seam: forget everything reported so far. Production never resets. */
    public static void resetForTest() {
        state = State.UNKNOWN;
        reason = "";
    }
}
