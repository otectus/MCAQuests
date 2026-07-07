package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** One completed-quest line in the journal archive: its display title and how many times completed (0.7.0). */
public record JournalArchiveEntry(Component questTitle, int count) {

    public static void encode(FriendlyByteBuf buf, JournalArchiveEntry entry) {
        buf.writeComponent(entry.questTitle);
        buf.writeVarInt(entry.count);
    }

    public static JournalArchiveEntry decode(FriendlyByteBuf buf) {
        return new JournalArchiveEntry(buf.readComponent(), buf.readVarInt());
    }
}
