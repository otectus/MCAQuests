package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.JournalArchiveEntry;
import dev.otectus.mcaquests.network.JournalSyncS2CPacket;
import dev.otectus.mcaquests.network.JournalVillageEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Client-side cache of the player's progression journal, updated by {@code JournalSyncS2CPacket} (0.7.0). */
public final class ClientJournalData {

    private static volatile List<Component> globalTitles = List.of();
    private static volatile List<JournalVillageEntry> villages = List.of();
    private static volatile List<JournalArchiveEntry> archive = List.of();

    private ClientJournalData() {
    }

    public static void update(JournalSyncS2CPacket packet) {
        globalTitles = List.copyOf(packet.globalTitles());
        villages = List.copyOf(packet.villages());
        archive = List.copyOf(packet.archive());
    }

    public static List<Component> globalTitles() {
        return globalTitles;
    }

    public static List<JournalVillageEntry> villages() {
        return villages;
    }

    public static List<JournalArchiveEntry> archive() {
        return archive;
    }
}
