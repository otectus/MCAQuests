package dev.otectus.mcaquests.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public static void encode(RegistryFriendlyByteBuf buf, JournalVillageEntry entry) {
        buf.writeResourceLocation(entry.dimension);
        buf.writeVarInt(entry.villageId);
        NetComponents.write(buf, entry.villageName);
        buf.writeVarInt(entry.reputation);
        NetComponents.write(buf, entry.currentTier);
        NetComponents.write(buf, entry.nextTier);
        buf.writeVarInt(entry.nextThreshold);
        buf.writeCollection(entry.titles, NetComponents::write);
    }

    public static JournalVillageEntry decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        int villageId = buf.readVarInt();
        Component name = NetComponents.read(buf);
        int reputation = buf.readVarInt();
        Component current = NetComponents.read(buf);
        Component next = NetComponents.read(buf);
        int nextThreshold = buf.readVarInt();
        List<Component> titles = buf.readCollection(ArrayList::new, NetComponents::read);
        return new JournalVillageEntry(dimension, villageId, name, reputation, current, next,
                nextThreshold, titles);
    }
}