package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.quest.target.StructureTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The way, from where the player is standing, into the dimension a quest wants.
 *
 * <p>A quest that says "go to the Nether" used to point at nothing at all, and once the player was in
 * the Nether the mod's only navigation line went on naming an overworld villager. Both halves of that
 * are answered here: this looks for the route <em>in the dimension the player is currently in</em>,
 * and {@code GuidanceService} stops asking the moment the objective is satisfied.
 *
 * <p>Deliberately honest about what it cannot answer. With no portal in range there is no marker —
 * pointing at open ground and calling it a route is worse than saying nothing, because the player
 * would walk to it.
 */
public final class Portals {

    /** Blocks around the player worth looking for a portal in. Past that, the walk <em>is</em> the quest. */
    private static final int PORTAL_SEARCH_RADIUS = 192;
    /** Chunks a stronghold search may walk. Vanilla's own {@code /locate} reach. */
    private static final int STRONGHOLD_SEARCH_CHUNKS = 100;

    private static final ResourceLocation STRONGHOLD = new ResourceLocation("minecraft", "stronghold");

    private Portals() {
    }

    /**
     * Where to go, in {@code level}, to reach {@code destination} — or empty when there is nothing
     * honest to point at.
     *
     * <ul>
     *   <li>The Nether from anywhere, or the overworld from the Nether: the nearest lit nether
     *       portal, read out of the level's point-of-interest index rather than by scanning blocks.</li>
     *   <li>The End: the nearest stronghold, which is where the portal into it is.</li>
     *   <li>Anything else — a modded dimension, the overworld from the End: empty. The mod does not
     *       know how that world is entered and will not guess.</li>
     * </ul>
     */
    public static Optional<BlockPos> routeTo(ServerLevel level, BlockPos from,
                                             ResourceKey<Level> destination) {
        if (level.dimension().equals(destination)) {
            return Optional.empty(); // already there, and the objective is about to complete
        }
        if (Level.END.equals(destination)) {
            return new StructureTarget(Optional.of(STRONGHOLD), Optional.empty())
                    .locate(level, from, STRONGHOLD_SEARCH_CHUNKS);
        }
        boolean netherHop = Level.NETHER.equals(destination)
                || (Level.NETHER.equals(level.dimension()) && Level.OVERWORLD.equals(destination));
        return netherHop ? nearestNetherPortal(level, from) : Optional.empty();
    }

    /**
     * The nearest lit nether portal to {@code from}.
     *
     * <p>Read from {@link PoiManager}, which the game already maintains for every portal block as it
     * is lit, so this costs an index lookup rather than a block scan — which is why, unlike the
     * structure and biome searches, it needs no {@link LocateCache} and can follow the player as they
     * move. {@code Occupancy.ANY} because a portal is never "occupied" the way a job site is.
     */
    private static Optional<BlockPos> nearestNetherPortal(ServerLevel level, BlockPos from) {
        try {
            return level.getPoiManager().findClosest(
                    holder -> holder.is(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                            PoiTypes.NETHER_PORTAL.location())),
                    from, PORTAL_SEARCH_RADIUS, PoiManager.Occupancy.ANY);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
