package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The last state Townstead saw for one villager, loaded or not, normalised for MCA: Quests.
 *
 * <p>Townstead 0.8 keeps a resident register: a roll of last-known readings with their age, not a
 * simulation. Needs do not advance while a villager is unloaded, so a reading here is exactly as
 * old as {@link #lastSeenWorldDay()} says and no fresher. Everything that judges a village on
 * these must say how old a reading it will accept; {@code TownsteadResidentEvidence} is that policy.
 *
 * <p>Only the needs the register stores are carried. Saturation, quenched and the exhaustion
 * counters are not recorded by Townstead and read as zero in {@link #needs()}, which is why the
 * wellbeing objectives only ever compare hunger, thirst, energy and collapse.
 *
 * @param villageDimension the dimension of the village this record files the villager under, or
 *                         {@code null} when the register knows no village for them
 * @param villageId        the MCA village id under that dimension, meaningful only with a dimension
 * @param loaded           true when the villager was in the world at the moment of the read, in which
 *                         case Townstead refreshed the record first and the reading is live
 * @param alive            false once Townstead has recorded the villager's death
 */
public record TownsteadResidentRecordView(
        UUID uuid,
        String name,
        @Nullable ResourceLocation villageDimension,
        int villageId,
        String professionId,
        int professionTier,
        TownsteadNeedsView needs,
        boolean loaded,
        boolean alive,
        long lastSeenGameTime,
        long lastSeenWorldDay) {

    /** The village this record belongs to, when Townstead filed it under one. */
    public Optional<ResourceLocation> village() {
        return Optional.ofNullable(villageDimension);
    }

    /** True when the record files this villager under the given village. */
    public boolean belongsTo(@Nullable ResourceLocation dimension, int id) {
        return villageDimension != null && villageDimension.equals(dimension) && villageId == id;
    }
}
