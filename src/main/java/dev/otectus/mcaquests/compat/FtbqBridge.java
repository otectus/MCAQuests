package dev.otectus.mcaquests.compat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Seam to the optional FTB Quests integration. The default NOOP instance is replaced by
 * FtbqBootstrap.init() iff the "ftbquests" mod is present AND init succeeds. Every method
 * on every implementation is fail-safe: never throws, returns the documented default.
 * Only java.* / net.minecraft.* types may appear in this interface.
 */
public interface FtbqBridge {

    enum ProgressAction { COMPLETE_TASK, COMPLETE_QUEST, RESET_TASK }

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
