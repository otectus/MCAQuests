package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Stable foreign identifiers for a bound Townstead civic building; no Townstead object is persisted. */
public record CivicBuildingBinding(UUID bindingId, UUID settlementId, ResourceLocation dimension,
                                   int villageId, int buildingId, String family, String typeAtBinding) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Binding", bindingId);
        tag.putUUID("Settlement", settlementId);
        tag.putString("Dimension", dimension.toString());
        tag.putInt("Village", villageId);
        tag.putInt("Building", buildingId);
        tag.putString("Family", family);
        tag.putString("Type", typeAtBinding);
        return tag;
    }

    public static Optional<CivicBuildingBinding> load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Binding") || !tag.hasUUID("Settlement")) return Optional.empty();
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || tag.getInt("Village") < 0 || tag.getInt("Building") < 0
                || tag.getString("Family").isBlank() || tag.getString("Type").isBlank()) return Optional.empty();
        return Optional.of(new CivicBuildingBinding(tag.getUUID("Binding"), tag.getUUID("Settlement"), dimension,
                tag.getInt("Village"), tag.getInt("Building"), tag.getString("Family"), tag.getString("Type")));
    }
}
