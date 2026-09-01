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

    /** The pre-1.4.3 form, which could not say whose kin were missing. */
    public static TriggerSignal missingKin(@Nullable ServerLevel level, int villageId,
                                           @Nullable UUID familyRootUuid) {
        return missingKin(level, villageId, null, familyRootUuid);
    }

    /**
     * A resident of {@code villageId} has a relative who has gone missing.
     *
     * <p>{@code villagerUuid} is the resident who is <em>missing someone</em>, not the missing person —
     * they are, by definition, nowhere. It has to be carried because {@code MissingKinTrigger} narrows on
     * which relation went missing, and that question can only be asked of a specific villager. Without it
     * the trigger's {@code relation} was unanswerable, which is why it sat unread and
     * {@code find_missing_child} opened for a missing spouse.
     */
    public static TriggerSignal missingKin(@Nullable ServerLevel level, int villageId,
                                           @Nullable UUID villagerUuid, @Nullable UUID familyRootUuid) {
        return new TriggerSignal(SituationSignalType.MISSING_KIN, level, villageId,
                villagerUuid, familyRootUuid, 0f, 0, false);
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
        return townsteadSpirit(level, villageId, identity, oldTier, newTier, null, null);
    }

    /**
     * As above, additionally carrying the classification either side of the change, so a definition can
     * ask "has this village become a blend" rather than only "which spirit went up".
     */
    public static TriggerSignal townsteadSpirit(@Nullable ServerLevel level, int villageId,
                                                String identity, int oldTier, int newTier,
                                                @Nullable String fromClassification,
                                                @Nullable String toClassification) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_SPIRIT, level, villageId, null, null,
                0f, newTier, false, SignalContext.spiritChange(identity, oldTier, newTier,
                        fromClassification, toClassification));
    }

    public static TriggerSignal townsteadBuilding(@Nullable ServerLevel level, int villageId,
                                                  String buildingType, int level0) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_BUILDING, level, villageId, null, null,
                0f, level0, false, SignalContext.identity(null, buildingType));
    }

    // --- 1.4.1 transitions (spec 5.8) ------------------------------------------------------------

    /** The calendar has crossed into a new week, season or year. */
    public static TriggerSignal townsteadCalendarTransition(@Nullable ServerLevel level, int villageId,
                                                            String period, String from, String to) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_CALENDAR_TRANSITION, level, villageId,
                null, null, 0f, 0, false, SignalContext.transition(period, from, to));
    }

    /**
     * A villager has crossed a life threshold. The subject travels with the signal so the offer can
     * prefer and bind the person it is about, rather than whichever neighbour was nearest.
     */
    public static TriggerSignal townsteadLifeTransition(@Nullable ServerLevel level, int villageId,
                                                        @Nullable UUID villagerUuid, String axis,
                                                        String from, String to) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_LIFE_TRANSITION, level, villageId,
                villagerUuid, null, 0f, 0, false, SignalContext.transition(axis, from, to));
    }

    /** Enough of a village has been off its own schedule for long enough to be news. */
    public static TriggerSignal townsteadScheduleDisruption(@Nullable ServerLevel level, int villageId,
                                                            float fraction, int observed) {
        return new TriggerSignal(SituationSignalType.TOWNSTEAD_SCHEDULE_DISRUPTION, level, villageId,
                null, null, fraction, observed, false, null);
    }

    /** A resident is {@code distance} blocks outside their home village and has stayed there. */
    public static TriggerSignal villagerStranded(@Nullable ServerLevel level, int villageId,
                                                 @Nullable UUID villagerUuid, int distance, boolean night) {
        return new TriggerSignal(SituationSignalType.VILLAGER_STRANDED, level, villageId, villagerUuid,
                null, 0f, distance, night, null);
    }

    /** {@code count} hostiles are gathered around this resident's bed or the village centre. */
    public static TriggerSignal hostilesNearHome(@Nullable ServerLevel level, int villageId,
                                                 @Nullable UUID villagerUuid, int count) {
        return new TriggerSignal(SituationSignalType.HOSTILES_NEAR_HOME, level, villageId, villagerUuid,
                null, 0f, count, false, null);
    }
}
