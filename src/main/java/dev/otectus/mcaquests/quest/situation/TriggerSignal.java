package dev.otectus.mcaquests.quest.situation;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * A detected gameplay event scoped to one village (0.8.0), produced by the detectors and consumed by
 * {@link SituationManager}. Carries the small bag of facts the various {@link SituationTrigger}s need to
 * decide whether to fire ({@code fraction} for infection progress, {@code magnitude} for a food count,
 * {@code fullMoon} for the night trigger), plus the focal villager / family lineage where relevant.
 *
 * <p>{@code level} may be {@code null} in unit tests; trigger matching never dereferences it.
 */
public record TriggerSignal(
        SituationSignalType type,
        @Nullable ServerLevel level,
        int villageId,
        @Nullable UUID villagerUuid,
        @Nullable UUID familyRootUuid,
        float fraction,
        int magnitude,
        boolean fullMoon) {

    public Optional<UUID> villager() {
        return Optional.ofNullable(villagerUuid);
    }

    public Optional<UUID> familyRoot() {
        return Optional.ofNullable(familyRootUuid);
    }

    public static TriggerSignal raid(@Nullable ServerLevel level, int villageId) {
        return new TriggerSignal(SituationSignalType.RAID, level, villageId, null, null, 0f, 0, false);
    }

    public static TriggerSignal villagerDeath(@Nullable ServerLevel level, int villageId,
                                              @Nullable UUID villagerUuid, @Nullable UUID familyRootUuid) {
        return new TriggerSignal(SituationSignalType.VILLAGER_DEATH, level, villageId,
                villagerUuid, familyRootUuid, 0f, 0, false);
    }

    public static TriggerSignal infection(@Nullable ServerLevel level, int villageId,
                                          @Nullable UUID villagerUuid, float progress) {
        return new TriggerSignal(SituationSignalType.INFECTION, level, villageId,
                villagerUuid, null, progress, 0, false);
    }

    public static TriggerSignal missingKin(@Nullable ServerLevel level, int villageId,
                                           @Nullable UUID familyRootUuid) {
        return new TriggerSignal(SituationSignalType.MISSING_KIN, level, villageId,
                null, familyRootUuid, 0f, 0, false);
    }

    public static TriggerSignal lowFood(@Nullable ServerLevel level, int villageId, int foodCount) {
        return new TriggerSignal(SituationSignalType.LOW_FOOD, level, villageId, null, null, 0f, foodCount, false);
    }

    public static TriggerSignal night(@Nullable ServerLevel level, int villageId, boolean fullMoon) {
        return new TriggerSignal(SituationSignalType.NIGHT, level, villageId, null, null, 0f, 0, fullMoon);
    }
}
