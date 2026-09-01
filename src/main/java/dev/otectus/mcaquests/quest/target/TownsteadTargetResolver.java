package dev.otectus.mcaquests.quest.target;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Turns a {@link TownsteadTarget} into an actual villager, reusing the resolvers MCA: Quests already
 * has rather than inventing a second targeting system (Townstead spec §6).
 *
 * <p>Two entry points, because the two callers know different amounts. At <b>offer</b> time there is no
 * accepted quest yet, so a condition can only ask about the giver, a relative, or someone nearby. Once a
 * quest is <b>active</b>, an objective can additionally ask for the villager it has already bound — and
 * that binding is what keeps a frozen baseline attached to the same person even after they wander off,
 * unload, or die.
 *
 * <p><b>A target is never re-picked once frozen.</b> Re-resolving {@code nearest} on a later pass would
 * silently move a baseline to a different villager and let a quest complete against someone it was never
 * about.
 */
public final class TownsteadTargetResolver {

    private TownsteadTargetResolver() {
    }

    /**
     * The villager a condition is about, evaluated against a giver at offer time.
     * {@link TownsteadTarget#VILLAGE_ANY} resolves to nothing here — it names a group, and the callers
     * that understand groups use {@link #residents} instead.
     */
    public static Optional<Entity> resolveForOffer(TownsteadTarget target, ServerPlayer player,
                                                   @Nullable Entity giver, ServerLevel level) {
        return switch (target) {
            case GIVER, BOUND, RECIPIENT -> Optional.ofNullable(giver);
            // "reachable", not the unfiltered walk: a Townstead reading taken from one of the two
            // deceased parents MCA invents for every villager it spawns is not a reading of anybody.
            // level.getEntity can still answer null for someone merely unloaded, and Optional.map on a
            // null result is empty, which is the pause this caller already expects.
            case RELATED -> Optional.ofNullable(giver)
                    .flatMap(g -> McaCompat.findGiverRelative(level, g, "any",
                            RelativeCandidate.DEFAULT_FAMILY_REQUIRE))
                    .map(level::getEntity)
                    .filter(McaCompat::isMcaVillager);
            case NEAREST -> McaCompat.nearestVillagerWithin(player, NEAREST_RADIUS);
            case VILLAGE_ANY -> Optional.empty();
        };
    }

    /**
     * The villager an objective is about. {@code bound} prefers the UUID this objective froze, so a
     * baseline stays attached to one person; every other selector falls back to the offer-time rules.
     */
    public static Optional<Entity> resolveForObjective(TownsteadTarget target, ServerPlayer player,
                                                       ActiveQuest active, ObjectiveProgress progress,
                                                       ServerLevel level) {
        UUID bound = progress.targetUuid();
        if (bound != null) {
            Entity entity = level.getEntity(bound);
            if (entity != null) {
                return Optional.of(entity);
            }
            // Bound but not loaded: deliberately do NOT fall through to another villager. The objective
            // reports missing and waits, which is what keeps a frozen baseline honest.
            return Optional.empty();
        }
        Entity giver = level.getEntity(active.villagerUuid());
        return resolveForOffer(target, player, giver, level);
    }

    /**
     * Residents of the giver's home village, capped and offset so a large village is visited
     * round-robin across passes instead of costing an unbounded scan every second
     * (Townstead spec §5.3). Only loaded villagers can be read, so this is what "observed" means for
     * the aggregate objectives.
     *
     * @param rotation a value that changes between passes (the game time works) so successive passes
     *                 start at a different offset and nobody is skipped forever
     */
    public static List<Entity> residents(ServerLevel level, @Nullable Entity giver, long rotation) {
        if (giver == null) {
            return List.of();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        if (villageId.isEmpty()) {
            return List.of();
        }
        List<Entity> all = McaCompat.loadedVillageResidents(level, villageId.getAsInt());
        int cap = McaQuestsConfig.COMMON.townsteadMaxVillagersPerPass.get();
        if (all.size() <= cap) {
            return all;
        }
        List<Entity> window = new ArrayList<>(cap);
        int start = (int) Math.floorMod(rotation, all.size());
        for (int i = 0; i < cap; i++) {
            window.add(all.get((start + i) % all.size()));
        }
        return window;
    }

    /** Matches {@code VillagerTarget}'s own fallback scan, so "nearest" means the same thing everywhere. */
    private static final double NEAREST_RADIUS = 48.0D;
}
