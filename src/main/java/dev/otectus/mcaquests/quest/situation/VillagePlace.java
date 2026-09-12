package dev.otectus.mcaquests.quest.situation;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/** One MCA village in one dimension: the unit the situation sweep and Townstead's events both address. */
public record VillagePlace(@Nullable ServerLevel level, int villageId) {
}
