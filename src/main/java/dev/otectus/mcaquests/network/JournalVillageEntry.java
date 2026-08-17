package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One village's standing in the journal (0.7.0): display name, current reputation, the current and next
 * tier names, the next tier's threshold ({@code -1} when already at the top), and the titles the player
 * has earned with this village.
 *
 * <p>Since the View Deeds link (§29.7) the entry also carries the village's <b>identity</b> —
 * dimension and id — so the client can name the community it wants opened without guessing. The
 * server still validates the pair before honouring the request; the identity here is an address, not
 * an authorization.
 */
public record JournalVillageEntry(ResourceLocation dimension, int villageId, Component villageName,
                                  int reputation, Component currentTier,
                                  Component nextTier, int nextThreshold, List<Component> titles) {

    public static void encode(FriendlyByteBuf buf, JournalVillageEntry entry) {
        buf.writeResourceLocation(entry.dimension);
        buf.writeVarInt(entry.villageId);
        buf.writeComponent(entry.villageName);
        buf.writeVarInt(entry.reputation);
        buf.writeComponent(entry.currentTier);
        buf.writeComponent(entry.nextTier);
        buf.writeVarInt(entry.nextThreshold);
        buf.writeCollection(entry.titles, FriendlyByteBuf::writeComponent);
    }

    public static JournalVillageEntry decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        int villageId = buf.readVarInt();
        Component name = buf.readComponent();
        int reputation = buf.readVarInt();
        Component current = buf.readComponent();
        Component next = buf.readComponent();
        int nextThreshold = buf.readVarInt();
        List<Component> titles = buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent);
        return new JournalVillageEntry(dimension, villageId, name, reputation, current, next,
                nextThreshold, titles);
    }
}