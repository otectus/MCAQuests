package dev.otectus.mcaquests.state;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * The two {@link Component} text conversions the player-state NBT needs, in one place (spec section 15).
 *
 * <p>PORT: 1.21 moved {@code Component.Serializer.toJson/fromJson} to take a
 * {@link net.minecraft.core.HolderLookup.Provider}, because a component can now carry registry-bound
 * data (item hover events, dialog styles). Nothing this mod stores does: a saved component is either a
 * villager's display name or a voiced offer line, both plain literal/translatable text built by us.
 * So {@link RegistryAccess#EMPTY} is threaded here rather than plumbed through {@code ActiveQuest.save},
 * {@code OfferSession.save} and every {@link PlayerQuestData} caller, which would also have forced a
 * provider into {@link ForgeCapsMigration}, where there is none. The JSON string produced is identical
 * to the 1.20.1 one, so the NBT keys and their contents round-trip across the upgrade unchanged.
 */
public final class NbtComponents {

    private NbtComponents() {
    }

    /** Serialises {@code component} to the same JSON string the 1.20.1 build wrote. */
    public static String toJsonString(Component component) {
        return Component.Serializer.toJson(component, RegistryAccess.EMPTY);
    }

    /** Reads back {@link #toJsonString}; null for JSON that is empty or does not describe a component. */
    @Nullable
    public static Component fromJsonString(String json) {
        return Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
    }
}
