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
}
