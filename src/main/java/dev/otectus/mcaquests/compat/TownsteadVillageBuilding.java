package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;

/**
 * One building MCA has registered for a village, as enumerated across the whole village rather than
 * looked up at a position.
 *
 * <p>Separate from {@link TownsteadBuildingView} on purpose. That view comes from Townstead's
 * positional API and carries full bounds; this one comes from MCA's building registry and honestly
 * carries only what that registry cheaply gives — half-filling the richer record with a centre
 * repeated as its own min and max would read like real bounds and quietly break any objective that
 * trusted them.
 *
 * <p>Buildings are an <b>MCA</b> concept that Townstead contributes type ids to, which is why counting
 * docks in a village is an MCA read cross-referenced against Townstead's ids rather than a Townstead
 * call. Tier parsing is shared with {@link TownsteadBuildingView} so the two can never disagree.
 */
public record TownsteadVillageBuilding(int id, String type, int size, BlockPos center) {

    /** The tier in a {@code _lN} suffix, or {@code 1} for an untiered building. */
    public int level() {
        return TownsteadBuildingView.levelOf(type);
    }

    /** The type id with any {@code _lN} suffix removed, so all three dock levels share a family. */
    public String family() {
        return TownsteadBuildingView.familyOf(type);
    }

    /** True when this building is the named type or a tier of it: {@code dock} matches {@code dock_l2}. */
    public boolean matches(String wanted) {
        return type.equals(wanted) || family().equals(wanted);
    }
}
