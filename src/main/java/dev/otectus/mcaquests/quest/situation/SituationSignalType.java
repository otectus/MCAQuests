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
    NIGHT
}
