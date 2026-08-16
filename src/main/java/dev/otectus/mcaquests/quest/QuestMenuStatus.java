package dev.otectus.mcaquests.quest;

/**
 * High-level state the villager's quest menu can be in (spec section 8). Phase 0 only ever uses
 * {@link #NO_QUESTS}; the rest land as the quest engine is built.
 */
public enum QuestMenuStatus {
    NO_QUESTS,
    OFFER,
    IN_PROGRESS,
    READY,
    BLOCKED
}
