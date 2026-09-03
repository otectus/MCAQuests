package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One waypoint, exactly as the map should show it.
 *
 * <p>The whole record is the desired state, and the whole record is its identity. That is the point:
 * the old sync compared position, dimension and label only, so a destination whose {@link GuidanceKind}
 * changed under it — a villager objective becoming a workstation one on the same block — kept the old
 * colour and the old initials for as long as the quest lasted. Anything a backend draws belongs in
 * here, so that "equal to what I applied" is the same question as "nothing to do".
 *
 * @param key       stable for the life of one quest's destination, so a quest whose objective advances
 *                  moves its waypoint instead of collecting a second one
 * @param pos       always paired with {@code dimension}; the map layer never scales a coordinate
 * @param label     already localized and rendered to a string, because a backend takes text rather
 *                  than a {@code Component}, and because two {@code Component}s that render the same
 *                  string are not equal
 * @param ownership who the waypoint belongs to; see {@link Ownership}
 */
public record WaypointSpec(
        String key,
        BlockPos pos,
        ResourceKey<Level> dimension,
        String label,
        GuidanceKind kind,
        WaypointSpec.Ownership ownership) {

    /**
     * Whether this mod may take the waypoint away again.
     *
     * <p>The distinction the old {@code clear()} lost. Automatic points belong to the quest and go
     * when it ends; a pin belongs to the player, and a cleanup that cannot tell the two apart deletes
     * something they asked for.
     */
    public enum Ownership {
        /** Published and withdrawn by the reconciler as guidance changes. */
        AUTOMATIC,
        /** Dropped by the player from the quest log. Nothing here ever removes one. */
        PIN
    }
}
