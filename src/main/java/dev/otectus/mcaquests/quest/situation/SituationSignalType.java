package dev.otectus.mcaquests.quest.situation;

/**
 * The kind of detected gameplay event a {@link dev.otectus.mcaquests.quest.situation.SituationManager}
 * dispatches to matching {@link SituationDefinition}s (0.8.0). Each {@link SituationTrigger} declares the
 * one signal type it consumes (see {@link SituationTrigger#signalType()}), so the manager only tests the
 * relevant definitions for a given signal.
 */
public enum SituationSignalType {
    RAID,
    VILLAGER_DEATH,
    INFECTION,
    MISSING_KIN,
    LOW_FOOD,
    NIGHT,

    // Townstead (Townstead spec 7.3). Appended, never reordered: the ordinal is one term of the
    // per-village draw seed in SituationManager.onSignal, so inserting above would silently reshuffle
    // which situation an existing village opens on an existing day.
    /** A need -- hunger, thirst, energy -- has crossed into crisis across a village. */
    TOWNSTEAD_NEED,
    /** A villager has just collapsed. Fires on the transition, never once per scan. */
    TOWNSTEAD_COLLAPSE,
    /** A villager has risen a profession tier. */
    TOWNSTEAD_PROFESSION_TIER,
    /** A village spirit has gained a tier, or settled into a different identity. */
    TOWNSTEAD_SPIRIT,
    /** A registered building has appeared, been upgraded, or been lost. */
    TOWNSTEAD_BUILDING,

    // 1.4.1. Appended for the same reason as the block above: the ordinal seeds the per-village draw,
    // so inserting rather than appending would reshuffle which situation an existing village opens.
    /** The Townstead calendar has turned over a week, a season or a year. */
    TOWNSTEAD_CALENDAR_TRANSITION,
    /** A villager has crossed a life threshold: come of age, or become a senior. */
    TOWNSTEAD_LIFE_TRANSITION,
    /** Enough of a village has been off its own schedule for long enough to be worth noticing. */
    TOWNSTEAD_SCHEDULE_DISRUPTION,
    /** A resident is far outside their home village after dark and has not moved on. MCA only. */
    VILLAGER_STRANDED,
    /** Hostile mobs have gathered at a resident's bed or the village centre. MCA only. */
    HOSTILES_NEAR_HOME
}
