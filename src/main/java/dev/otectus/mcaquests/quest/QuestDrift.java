package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.state.ActiveQuest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a datapack edit has changed the objectives an accepted quest was accepted with (1.7.0).
 *
 * <p>Progress is saved by position: objective 0's count, objective 1's count. Until 1.7.0 an edit that
 * inserted, removed or reordered objectives silently reinterpreted that progress — a count earned for
 * killing zombies became progress toward visiting a biome. Each accepted quest now remembers a
 * fingerprint of every objective ({@link DefinitionFingerprint}); when the current definition no longer
 * matches them position by position, the quest is <b>paused</b> with the reason "definition changed", its
 * clock frozen and its progress untouched, until an operator accepts the new definition
 * ({@code /mcaquests quest rebase}) or the edit is reverted. Appending objectives after the existing ones
 * is still supported, as it always was, and is not drift.
 *
 * <p>A quest saved before 1.7.0 has no fingerprints; the first check records the definition it finds,
 * since nothing can say what it was accepted against.
 */
public final class QuestDrift {

    /**
     * Fingerprints of a resolved definition's objectives, computed once per definition <em>object</em>:
     * keyed by identity, because a record's own hash walks every component on each event. Cleared when it
     * grows past a reload's worth of definitions.
     */
    private record Cached(QuestDefinition definition, List<String> fingerprints) {
    }

    private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 4096;

    private QuestDrift() {
    }

    public static List<String> fingerprints(QuestDefinition resolved) {
        int key = System.identityHashCode(resolved);
        Cached cached = CACHE.get(key);
        if (cached != null && cached.definition() == resolved) {
            return cached.fingerprints();
        }
        List<String> computed = List.copyOf(DefinitionFingerprint.ofEach(ObjectiveTypes.CODEC, resolved.objectives()));
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, new Cached(resolved, computed));
        return computed;
    }

    /** Records the definition an accepted quest was accepted against. */
    public static void capture(ActiveQuest active, QuestDefinition resolved) {
        active.setObjectiveFingerprints(fingerprints(resolved));
    }

    /**
     * True when {@code resolved} is not the definition {@code active} was accepted against. A quest with
     * no record takes this one; a pure append is adopted.
     */
    public static boolean drifted(ActiveQuest active, QuestDefinition resolved) {
        List<String> accepted = active.objectiveFingerprints();
        List<String> current = fingerprints(resolved);
        if (accepted.isEmpty()) {
            active.setObjectiveFingerprints(current);
            return false;
        }
        if (!compatible(accepted, current)) {
            return true;
        }
        if (current.size() > accepted.size()) {
            active.setObjectiveFingerprints(current);
        }
        return false;
    }

    /**
     * Every objective the quest was accepted with is still in the same place, unchanged. An entry that
     * could not be fingerprinted on either side is taken on trust rather than pausing a quest over a
     * codec that cannot encode.
     */
    public static boolean compatible(List<String> accepted, List<String> current) {
        if (current.size() < accepted.size()) {
            return false;
        }
        for (int i = 0; i < accepted.size(); i++) {
            String before = accepted.get(i);
            String now = current.get(i);
            if (!before.isEmpty() && !now.isEmpty() && !before.equals(now)) {
                return false;
            }
        }
        return true;
    }
}
