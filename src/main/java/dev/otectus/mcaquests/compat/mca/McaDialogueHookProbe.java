package dev.otectus.mcaquests.compat.mca;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What became of the MCA dialogue hook's four variants (1.7.0), for {@code /mcaquests debug mca} and
 * for the conversation fallback's decision.
 *
 * <p>Kept apart from {@link McaGiftHookProbe} because the two hooks target different classes and can
 * fail independently: an MCA build could keep {@code handle} and move {@code interactAt}, or the other
 * way round, and each needs its own honest answer.
 */
public final class McaDialogueHookProbe {

    private static final Map<String, String> OUTCOMES = new LinkedHashMap<>();
    private static volatile boolean applied;
    private static volatile boolean observed;

    private McaDialogueHookProbe() {
    }

    public static synchronized void applied(String target) {
        applied = true;
        OUTCOMES.put(shortName(target), "applied");
    }

    public static synchronized void skipped(String target, String why) {
        OUTCOMES.putIfAbsent(shortName(target), "skipped: " + why);
    }

    public static synchronized void failed(String target, String why) {
        OUTCOMES.put(shortName(target), "FAILED: " + why);
    }

    /** A dialogue actually reached the hook on the server, which is stronger evidence than the byte check. */
    public static void observed() {
        observed = true;
        applied = true;
    }

    /** True when one variant applied: MCA's own dialogue signal is live, so the event fallback stands down. */
    public static boolean applied() {
        return applied;
    }

    public static boolean wasObserved() {
        return observed;
    }

    public static synchronized String describe() {
        if (OUTCOMES.isEmpty()) {
            return "not evaluated (MCA's interaction handler has not loaded yet)";
        }
        StringBuilder out = new StringBuilder();
        OUTCOMES.forEach((target, outcome) -> {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(target).append(' ').append(outcome);
        });
        return out.toString();
    }

    public static synchronized void resetForTest() {
        OUTCOMES.clear();
        applied = false;
        observed = false;
    }

    /** The package root, which is what tells the four variants apart. */
    private static String shortName(String target) {
        int entity = target.indexOf(".entity.");
        return entity < 0 ? target : target.substring(0, entity);
    }
}
