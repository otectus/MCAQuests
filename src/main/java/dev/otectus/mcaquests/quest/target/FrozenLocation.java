package dev.otectus.mcaquests.quest.target;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A destination decided once and then kept, whatever the world does afterwards (spec §5.4, §16.2).
 *
 * <p>Most {@link LocationAnchor} types resolve live on every poll, and for them that is right: a
 * village center does not move, and "the giver's bed" should follow the giver if they are rehoused.
 * The anchors added in 1.4.1 are different. "The nearest dock" and "the next village along" are
 * <em>choices</em>, and a choice that is re-made every second is not a destination — the map marker
 * would jump the moment a closer dock was built, and two objectives in the same quest could
 * legitimately disagree about which dock they meant.
 *
 * <p>So a frozen anchor is resolved once, written here, and read back thereafter. If the building is
 * later demolished the position remains a perfectly good place to walk to, build at, or defend;
 * objectives that need the building to still be <em>registered</em> read that live and separately.
 *
 * <p>{@link #SCHEMA} is written so a later release can add fields and ignore what it does not know,
 * rather than having to guess whether an absent key means "old save" or "not applicable".
 *
 * @param pos        the destination itself
 * @param dimension  the level it was resolved in; a frozen position in another dimension is not a
 *                   destination the player can walk to and must not be treated as one
 * @param villageId  the MCA village that owned it, when it had one — this is what lets arrival use the
 *                   village border rather than a radius
 * @param buildingId the registered building's id, for reading its live state back
 * @param family     the normalised building family that was chosen, for the card
 * @param tier       the building's tier when it was chosen
 */
public record FrozenLocation(BlockPos pos, ResourceLocation dimension, OptionalInt villageId,
                             OptionalInt buildingId, Optional<String> family, OptionalInt tier) {

    /** Bumped only for a change that existing saves cannot be read under. */
    public static final int SCHEMA = 1;

    private static final String K_SCHEMA = "schema";
    private static final String K_POS = "pos";
    private static final String K_DIMENSION = "dim";
    private static final String K_VILLAGE = "village";
    private static final String K_BUILDING = "building";
    private static final String K_FAMILY = "family";
    private static final String K_TIER = "tier";

    public static FrozenLocation of(BlockPos pos, ResourceLocation dimension) {
        return new FrozenLocation(pos, dimension, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty(), OptionalInt.empty());
    }

    public static FrozenLocation building(BlockPos pos, ResourceLocation dimension, int villageId,
                                          int buildingId, String family, int tier) {
        return new FrozenLocation(pos, dimension, OptionalInt.of(villageId), OptionalInt.of(buildingId),
                Optional.of(family), OptionalInt.of(tier));
    }

    public static FrozenLocation village(BlockPos pos, ResourceLocation dimension, int villageId) {
        return new FrozenLocation(pos, dimension, OptionalInt.of(villageId), OptionalInt.empty(),
                Optional.empty(), OptionalInt.empty());
    }

    /** The live-resolution shape the location objectives already speak. */
    public LocationAnchor.Resolved resolved() {
        return new LocationAnchor.Resolved(pos, villageId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(K_SCHEMA, SCHEMA);
        tag.putLong(K_POS, pos.asLong());
        tag.putString(K_DIMENSION, dimension.toString());
        villageId.ifPresent(id -> tag.putInt(K_VILLAGE, id));
        buildingId.ifPresent(id -> tag.putInt(K_BUILDING, id));
        family.ifPresent(f -> tag.putString(K_FAMILY, f));
        tier.ifPresent(t -> tag.putInt(K_TIER, t));
        return tag;
    }

    /** Reads one back, or empty when the tag is absent, from a future schema, or malformed. */
    @Nullable
    public static FrozenLocation load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(K_SCHEMA, Tag.TAG_INT) || tag.getInt(K_SCHEMA) > SCHEMA) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(K_DIMENSION));
        if (dimension == null) {
            return null;
        }
        return new FrozenLocation(
                BlockPos.of(tag.getLong(K_POS)),
                dimension,
                tag.contains(K_VILLAGE, Tag.TAG_INT) ? OptionalInt.of(tag.getInt(K_VILLAGE)) : OptionalInt.empty(),
                tag.contains(K_BUILDING, Tag.TAG_INT) ? OptionalInt.of(tag.getInt(K_BUILDING)) : OptionalInt.empty(),
                tag.contains(K_FAMILY, Tag.TAG_STRING) ? Optional.of(tag.getString(K_FAMILY)) : Optional.empty(),
                tag.contains(K_TIER, Tag.TAG_INT) ? OptionalInt.of(tag.getInt(K_TIER)) : OptionalInt.empty());
    }
}
