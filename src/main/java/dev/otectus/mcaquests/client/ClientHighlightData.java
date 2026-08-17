package dev.otectus.mcaquests.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

/**
 * Client-side set of the entity ids this player's active quests want outlined.
 *
 * <p>Read at render time by {@code MinecraftGlowMixin}, which forces
 * {@code Minecraft#shouldEntityAppearGlowing} true for anything in here. Because the decision is made per
 * frame rather than stamped onto the entity, there is <b>no per-entity state to undo</b>: when a villager
 * drops out of the set it simply stops glowing on the next frame.
 *
 * <p>Note for anyone tempted to simplify this to {@code Entity#setGlowingTag}: that does not work from the
 * client in 1.20.1. {@code setGlowingTag} ends with {@code setSharedFlag(6, isCurrentlyGlowing())}, and
 * {@code isCurrentlyGlowing()} on the client <em>reads</em> shared flag 6 — so the call writes the flag
 * back to its own current value and nothing happens.
 *
 * <p>Written from the network thread's work queue and read from the render thread, hence {@code volatile}
 * plus replace-don't-mutate, matching {@link ClientQuestData}'s style.
 */
public final class ClientHighlightData {

    private static volatile IntSet highlighted = IntSets.emptySet();

    private ClientHighlightData() {
    }

    public static void update(int[] entityIds) {
        highlighted = entityIds.length == 0 ? IntSets.emptySet() : new IntOpenHashSet(entityIds);
    }

    /** True when this entity is a target of one of the local player's active quests. */
    public static boolean isHighlighted(int entityId) {
        return highlighted.contains(entityId);
    }

    /** Drops everything — on disconnect, so an outline cannot survive into the next world. */
    public static void clear() {
        highlighted = IntSets.emptySet();
    }
}
