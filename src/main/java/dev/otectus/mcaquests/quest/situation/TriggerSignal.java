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
        boolean fullMoon,
        @Nullable SignalContext context) {

    /**
     * Keeps every existing eight-argument construction working unchanged by defaulting the context to
     * absent. The six factories below, and every call site of them, are untouched by this addition --
     * which is the point: a Townstead signal needed one more field, and the original six signals should
     * not have to care.
     */
    public TriggerSignal(SituationSignalType type, @Nullable ServerLevel level, int villageId,
                         @Nullable UUID villagerUuid, @Nullable UUID familyRootUuid, float fraction,
                         int magnitude, boolean fullMoon) {
        this(type, level, villageId, villagerUuid, familyRootUuid, fraction, magnitude, fullMoon, null);
    }

    /** The extra facts a Townstead signal carries, when it carries any. */
    public Optional<SignalContext> signalContext() {
        return Optional.ofNullable(context);
    }

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

    // --- Townstead (Townstead spec 7.3) ----------------------------------------------------------

    /** A village-wide need crisis: {@code fraction} of residents are below the threshold for {@code need}. */
    public static TriggerSignal townsteadNeed(@Nullable ServerLevel level, int villageId, String need,
                                              float fraction, double worst) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_NEED, level, villageId, null, null,
                fraction, 0, false, SignalContext.of(need, worst));
    }

    public static TriggerSignal townsteadCollapse(@Nullable ServerLevel level, int villageId,
                                                  @Nullable UUID villagerUuid) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_COLLAPSE, level, villageId, villagerUuid,
                null, 0f, 0, false, null);
    }

    public static TriggerSignal townsteadProfessionTier(@Nullable ServerLevel level, int villageId,
                                                        @Nullable UUID villagerUuid, String profession,
                                                        int oldTier, int newTier) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_PROFESSION_TIER, level, villageId,
                villagerUuid, null, 0f, newTier, false,
                SignalContext.tierChange(profession, oldTier, newTier));
    }

    public static TriggerSignal townsteadSpirit(@Nullable ServerLevel level, int villageId,
                                                String identity, int oldTier, int newTier) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_SPIRIT, level, villageId, null, null,
                0f, newTier, false, SignalContext.tierChange(identity, oldTier, newTier));
    }

    public static TriggerSignal townsteadBuilding(@Nullable ServerLevel level, int villageId,
                                                  String buildingType, int level0) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_BUILDING, level, villageId, null, null,
                0f, level0, false, SignalContext.identity(null, buildingType));
    }
}
