package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Which one of a player's active quests they are currently following.
 *
 * <p>Everything in the mod that points somewhere now points at this one quest: the world marker, the
 * guidance line on the tracker, and the villager outline. That is the whole reason the concept exists.
 * Highlighting used to be computed over <em>every</em> active quest at once, so a player holding five
 * of them had five glowing villagers and no way to tell which mattered; a marker system built the same
 * way would have been five beacons.
 *
 * <p>Identified the same way {@code QuestLogEntry} identifies a quest for its Abandon button — quest id
 * <em>and</em> giver — because the same quest can be active from two different villagers, and tracking
 * "the wheat delivery" would otherwise be ambiguous about which wheat delivery.
 *
 * <p>Stored as a plain reference rather than as a flag on {@link ActiveQuest} so that it is a property
 * of the player (they follow one thing at a time) rather than of each quest (which would let two be
 * tracked at once, or none after a load that missed a flag). A reference to a quest that is no longer
 * active is simply ignored and cleared on the next pass, so nothing has to be tidied up at the exact
 * moment a quest completes, fails or is abandoned.
 */
public record TrackedQuest(ResourceLocation questId, UUID villagerUuid) {

    public boolean matches(ActiveQuest quest) {
        return quest.questId().equals(questId) && quest.villagerUuid().equals(villagerUuid);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId.toString());
        tag.putUUID("villager", villagerUuid);
        return tag;
    }

    /**
     * Reads a tracked reference, or empty when there is none.
     *
     * <p>Tolerant of an absent or half-written tag, because this is new state landing in saves that do
     * not have it: a player who upgrades mid-quest simply has nothing tracked until the next quest they
     * accept, rather than a load failure.
     */
    public static Optional<TrackedQuest> load(CompoundTag tag) {
        if (!tag.contains("quest") || !tag.hasUUID("villager")) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("quest"));
        return id == null ? Optional.empty() : Optional.of(new TrackedQuest(id, tag.getUUID("villager")));
    }
}
