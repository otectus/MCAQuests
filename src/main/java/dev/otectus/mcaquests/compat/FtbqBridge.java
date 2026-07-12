package dev.otectus.mcaquests.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Seam to the optional FTB Quests integration. The default NOOP instance is replaced by
 * FtbqBootstrap.init() iff the "ftbquests" mod is present AND init succeeds. Every method
 * on every implementation is fail-safe: never throws, returns the documented default.
 * Only java.* / net.minecraft.* types may appear in this interface.
 */
public interface FtbqBridge {

    enum ProgressAction { COMPLETE_TASK, COMPLETE_QUEST, RESET_TASK }

    /**
     * One Book&nbsp;→&nbsp;MCA finding for {@code /mcaquests ftbq validate} (spec §21): an
     * {@code mcaquests:}-namespaced task/reward in the FTB book whose referenced MCA registry id
     * (quest/chain/ladder/tier/title/project/situation) is a problem. Deliberately FTB-free
     * (plain {@code String}s + a {@code boolean} only) so it can cross the {@link FtbqBridge} seam
     * and be formatted by the always-loaded command class without either side needing FTB types.
     *
     * <p>{@code malformed} distinguishes the two §21 severities: {@code true} means the id could
     * never resolve against any registry state (not a valid {@code ResourceLocation}, or a
     * chain/tier id containing the reserved {@code '|'} separator) — a genuine authoring error.
     * {@code false} means the id is well-formed but absent from the current registries — the
     * sanctioned forward-reference pattern (spec §20: authors legitimately reference ids from
     * datapacks they haven't written yet), reported as a warning, not an error.
     */
    record BookReference(String chapterName, String questCode, String taskName, String field, String unknownId,
                         boolean malformed) {
    }

    boolean isAvailable();                       // real impl present AND enableFtbQuestsIntegration

    /** ids are FTB 16-hex code strings, optional leading '#'. Absent/invalid/unknown → false. */
    boolean isQuestCompleted(ServerPlayer player, String hexId);
    boolean isChapterCompleted(ServerPlayer player, String hexId);
    boolean isTaskCompleted(ServerPlayer player, String hexId);

    /** true if the id parses AND resolves to an object of the right kind in the server book. */
    boolean questIdExists(String hexId);
    boolean chapterIdExists(String hexId);
    boolean taskIdExists(String hexId);

    /** Applies an FTB progress change for the player's team. False on any failure (logged DEBUG). */
    boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId);

    /** Re-evaluate all mcaquests-namespaced FTB tasks for this player (login, /recheck, admin ops). */
    void recheckAll(ServerPlayer player);

    /** Diagnostic counts for /mcaquests ftbq status: [mcaTasksInBook, mcaRewardsInBook]. */
    int[] integrationObjectCounts();

    /**
     * True only for the real, FTBQ-backed implementation — false for the {@code Noop} default. Lets
     * {@code /mcaquests ftbq status} distinguish "FTB Quests is present but the bridge failed to
     * initialize" from "the {@code enableFtbQuestsIntegration} master switch is off", which otherwise
     * look identical through {@link #isAvailable()} alone (both fold to {@code false}).
     */
    default boolean isReal() {
        return false;
    }

    /**
     * Book → MCA sweep for {@code /mcaquests ftbq validate} (spec §21): every {@code mcaquests:}
     * task/reward currently in the server book whose referenced MCA registry id is unknown. Empty
     * when unavailable, the book doesn't exist, or nothing is wrong — never {@code null}.
     */
    default List<BookReference> validateBookReferences() {
        return List.of();
    }

    final class Holder {
        private static volatile FtbqBridge instance = NoopFtbqBridge.INSTANCE;
        public static FtbqBridge get() { return instance; }
        /**
         * Called once from {@code FtbqBootstrap} (a different Java package, {@code compat.ftbq}),
         * so this must be {@code public} rather than the spec's literal package-private — Java
         * access control does not treat {@code compat.ftbq} as nested inside {@code compat}. Not
         * intended as public API for arbitrary callers; last writer wins.
         */
        public static void set(FtbqBridge b) { instance = b; }
    }
}
