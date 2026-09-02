package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.quest.QuestLogEntry;

import java.util.List;

/** Client-side cache of the player's active quests, updated by {@code QuestLogSyncS2CPacket}. */
public final class ClientQuestData {

    private static volatile List<QuestLogEntry> active = List.of();
    /** Transient HUD toggle driven by the keybind; the config's showQuestTrackerHud stays the master switch. */
    private static volatile boolean hudVisible = true;

    private ClientQuestData() {
    }

    public static void update(List<QuestLogEntry> entries) {
        active = List.copyOf(entries);
    }

    public static List<QuestLogEntry> active() {
        return active;
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    /**
     * Drops the cached log on logout, so no quest of the world just left can show on the next one's
     * tracker. The HUD toggle is deliberately kept: it is this player's display preference, not
     * anything the server told us.
     */
    public static void clear() {
        active = List.of();
    }
}
