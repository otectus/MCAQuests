package dev.otectus.mcaquests.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One map point a server integration has already decided this viewer may see (1.7.0). Published with
 * {@link McaQuestsApi#publishExternalMapPoints}; MCA: Quests validates it, networks it and draws it on the
 * supported map backends, but never owns or reconciles it the way it does quest destinations.
 *
 * @param key         stable within its owner, at most 160 characters
 * @param label       what the map shows, at most 128 characters
 * @param approximate the position is an area, not a spot
 * @param lastKnown   the position is where the thing was last seen, not where it is
 */
public record ExternalMapPoint(String key, ResourceKey<Level> dimension, BlockPos position, String label,
                               Kind kind, boolean approximate, boolean lastKnown) {

    public enum Kind { SITE, ROUTE }

    public ExternalMapPoint {
        if (key == null || key.isBlank() || key.length() > 160 || label == null || label.length() > 128
                || dimension == null || position == null || kind == null) {
            throw new IllegalArgumentException("invalid external map point");
        }
        position = position.immutable();
    }

    /** A dimension key from its id, refusing anything that is not a well-formed resource location. */
    public static ResourceKey<Level> dimension(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid dimension");
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
