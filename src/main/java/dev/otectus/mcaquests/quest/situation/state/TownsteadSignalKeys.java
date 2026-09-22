package dev.otectus.mcaquests.quest.situation.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * How {@link TownsteadSignalStateSavedData} keys are spelled, shared by the polling detector and the
 * event path so that both observe the <em>same</em> baseline for the same moment.
 *
 * <p>MCA allocates village ids per dimension, so village 3 of the overworld and village 3 of another
 * dimension are different places. Village-scoped keys therefore carry the dimension -- except for
 * the overworld, which keeps the bare id it has always had, so every baseline written by an earlier
 * release stays valid and nothing is re-announced on upgrade. Villager-scoped keys need no
 * qualifier: a UUID is unique on its own.
 */
public final class TownsteadSignalKeys {

    private TownsteadSignalKeys() {
    }

    /** The village prefix: {@code "12"} in the overworld, {@code "modid:dim|12"} anywhere else. */
    public static String village(@Nullable ServerLevel level, int villageId) {
        return village(level == null ? null : level.dimension().location(), villageId);
    }

    public static String village(@Nullable ResourceLocation dimension, int villageId) {
        if (dimension == null || dimension.equals(Level.OVERWORLD.location())) {
            return Integer.toString(villageId);
        }
        return dimension + "|" + villageId;
    }

    public static String villager(UUID uuid) {
        return uuid.toString();
    }

    /** The calendar baseline for one period of one village under one calendar profile. */
    public static String calendar(String profileId, String periodId, @Nullable ResourceLocation dimension,
                                  int villageId) {
        return "calendar|" + profileId + '|' + periodId + '|' + village(dimension, villageId);
    }
}
