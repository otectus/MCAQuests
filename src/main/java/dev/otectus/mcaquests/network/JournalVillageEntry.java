package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One village's standing in the journal (0.7.0): display name, current reputation, the current and next
 * tier names, the next tier's threshold ({@code -1} when already at the top), and the titles the player
 * has earned with this village.
 */
public record JournalVillageEntry(Component villageName, int reputation, Component currentTier,
                                  Component nextTier, int nextThreshold, List<Component> titles) {

    public static void encode(FriendlyByteBuf buf, JournalVillageEntry entry) {
        buf.writeComponent(entry.villageName);
        buf.writeVarInt(entry.reputation);
        buf.writeComponent(entry.currentTier);
        buf.writeComponent(entry.nextTier);
        buf.writeVarInt(entry.nextThreshold);
        buf.writeCollection(entry.titles, FriendlyByteBuf::writeComponent);
    }

    public static JournalVillageEntry decode(FriendlyByteBuf buf) {
        Component name = buf.readComponent();
        int reputation = buf.readVarInt();
        Component current = buf.readComponent();
        Component next = buf.readComponent();
        int nextThreshold = buf.readVarInt();
        List<Component> titles = buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent);
        return new JournalVillageEntry(name, reputation, current, next, nextThreshold, titles);
    }
}
