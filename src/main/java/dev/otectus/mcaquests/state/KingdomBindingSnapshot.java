package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Identifier-only kingdom context frozen onto one accepted quest. */
public record KingdomBindingSnapshot(UUID settlementId, ResourceLocation kingdomId,
                                     long settlementRevision, ResourceLocation dimension,
                                     Optional<ResourceLocation> localDimension, OptionalInt localVillageId) {
    public KingdomBindingSnapshot(UUID settlementId, ResourceLocation kingdomId,
                                  long settlementRevision, ResourceLocation dimension) {
        this(settlementId, kingdomId, settlementRevision, dimension, Optional.empty(), OptionalInt.empty());
    }

    public KingdomBindingSnapshot {
        localDimension = localDimension == null ? Optional.empty() : localDimension;
        localVillageId = localVillageId == null ? OptionalInt.empty() : localVillageId;
        if (localDimension.isPresent() != localVillageId.isPresent()) {
            throw new IllegalArgumentException("local community dimension and village id must appear together");
        }
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Settlement", settlementId);
        tag.putString("Kingdom", kingdomId.toString());
        tag.putLong("Revision", settlementRevision);
        tag.putString("Dimension", dimension.toString());
        localDimension.ifPresent(value -> tag.putString("LocalDimension", value.toString()));
        if (localVillageId.isPresent()) tag.putInt("LocalVillage", localVillageId.getAsInt());
        return tag;
    }

    public static Optional<KingdomBindingSnapshot> load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Settlement")) return Optional.empty();
        ResourceLocation kingdom = ResourceLocation.tryParse(tag.getString("Kingdom"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (kingdom == null || dimension == null || tag.getLong("Revision") < 0) return Optional.empty();
        ResourceLocation localDimension = tag.contains("LocalDimension")
                ? ResourceLocation.tryParse(tag.getString("LocalDimension")) : null;
        OptionalInt localVillage = tag.contains("LocalVillage")
                ? OptionalInt.of(tag.getInt("LocalVillage")) : OptionalInt.empty();
        if ((localDimension != null) != localVillage.isPresent()) return Optional.empty();
        return Optional.of(new KingdomBindingSnapshot(tag.getUUID("Settlement"), kingdom,
                tag.getLong("Revision"), dimension, Optional.ofNullable(localDimension), localVillage));
    }
}
