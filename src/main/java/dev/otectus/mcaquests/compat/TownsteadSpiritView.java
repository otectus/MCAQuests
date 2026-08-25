package dev.otectus.mcaquests.compat;

import java.util.Map;

/**
 * A village's Townstead "spirit" — the character a settlement acquires from what its people build
 * (Townstead spec §2.3).
 *
 * <p>Points accrue per spirit from completed buildings, and Townstead reduces the spread to a
 * {@link #classification()}: a village that has built nothing in particular reads as a plain
 * settlement, one with a clear favourite has a single identity, two close leaders blend, and a wide
 * even spread is mixed. Quests and projects use the classification and {@link #primaryId()} for
 * "what kind of place is this becoming", and {@link #tier()} for "how far along".
 *
 * <p>{@link #classification()} is a lowercase string rather than an enum on purpose: Townstead's
 * enum never crosses this boundary, so a future constant cannot break linkage here.
 */
public record TownsteadSpiritView(
        int villageId,
        Map<String, Integer> perSpirit,
        int total,
        int contributingBuildings,
        int tier,
        String classification,
        String primaryId,
        String secondaryId) {

    public TownsteadSpiritView {
        perSpirit = Map.copyOf(perSpirit);
    }

    /** Points for one spirit id, or {@code 0} if this village has none. */
    public int pointsFor(String spiritId) {
        return perSpirit.getOrDefault(spiritId, 0);
    }

    /** A spirit's share of the village total, {@code 0.0} when nothing has been built yet. */
    public double shareOf(String spiritId) {
        return total <= 0 ? 0.0D : (double) pointsFor(spiritId) / total;
    }
}
