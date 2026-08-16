package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.compat.ReputationBackend;
import dev.otectus.mcaquests.compat.ReputationBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The single Quests-side funnel for every reputation, tier, and title read and write (spec §29.1).
 *
 * <p>Before 1.1.0 these calls were scattered: conditions read {@code ProjectSavedData} directly, the
 * Journal read it again, {@code ReputationService.award} wrote it, and the FTB tasks did their own
 * thing. Any one of them could disagree with the others, and none of them could be redirected to
 * another backend. Everything now goes through this class, which does exactly two things:
 *
 * <ol>
 *   <li>resolves a village into a <b>dimension-aware</b> community, using the same home-village-then-
 *       nearest fallback the conditions have always used, so the value a condition reads is the value
 *       a reward wrote; and</li>
 *   <li>hands the request to {@link ReputationBridge#backend()} — MCA: Reputation when it is
 *       installed, Quests' own store when it is not.</li>
 * </ol>
 *
 * <p>Nothing here decides what anything is worth. Deltas come from datapacks, tier consequences from
 * whichever backend is live.
 */
public final class QuestReputation {

    /** The source id recorded against every incident Quests creates. */
    public static final ResourceLocation SOURCE = new ResourceLocation(McaQuests.MOD_ID, "quests");

    private QuestReputation() {
    }

    private static ReputationBackend backend() {
        return ReputationBridge.backend();
    }

    /** True when MCA: Reputation owns the canonical state. */
    public static boolean isCanonical() {
        return ReputationBridge.isCanonical();
    }

    // ------------------------------------------------------------------
    // Community resolution
    // ------------------------------------------------------------------

    /**
     * The community a villager belongs to: their home village, or the nearest one within the
     * configured fallback radius.
     *
     * <p>This is the historic {@code ScopeResolver} behaviour, kept identical so no existing quest
     * changes which village it talks about — the only difference is that the answer now carries the
     * dimension it was resolved in.
     */
    public static Optional<Community> resolve(@Nullable Entity villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(villager);
        if (villageId.isEmpty()) {
            villageId = McaCompat.findNearestVillageId(level, villager.blockPosition(),
                    McaQuestsConfig.COMMON.defaultScopeFallbackRadius.get());
        }
        return villageId.isPresent()
                ? Optional.of(new Community(level.dimension().location(), villageId.getAsInt()))
                : Optional.empty();
    }

    /** The nearest community to a position. */
    public static Optional<Community> resolveNearest(ServerLevel level, net.minecraft.core.BlockPos pos,
                                                     int radius) {
        OptionalInt villageId = McaCompat.findNearestVillageId(level, pos, radius);
        return villageId.isPresent()
                ? Optional.of(new Community(level.dimension().location(), villageId.getAsInt()))
                : Optional.empty();
    }

    /**
     * A community named by a bare village id in the given level.
     *
     * <p>Used where Quests already holds an id from its own saved state — a project scope, a situation
     * instance — and the dimension is known from context. New code should carry a whole
     * {@link Community} instead.
     */
    public static Community inLevel(ServerLevel level, int villageId) {
        return new Community(level.dimension().location(), villageId);
    }

    /** A dimension-aware community. The Quests-side equivalent of Reputation's {@code CommunityKey}. */
    public record Community(ResourceLocation dimension, int villageId) {

        public String asString() {
            return dimension + "/" + villageId;
        }
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public static int score(MinecraftServer server, UUID player, Community community) {
        try {
            return backend().score(server, player, community.dimension(), community.villageId());
        } catch (Throwable t) {
            return safeZero("score", t);
        }
    }

    public static int score(ServerPlayer player, Community community) {
        return score(player.server, player.getUUID(), community);
    }

    public static String tierId(MinecraftServer server, UUID player, Community community,
                                ResourceLocation ladder) {
        try {
            return backend().tierId(server, player, community.dimension(), community.villageId(), ladder);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] tierId failed; using the floor tier", t);
            return ReputationTiers.getOrDefault(ladder).tierFor(0).id();
        }
    }

    public static int tierIndex(MinecraftServer server, UUID player, Community community,
                                ResourceLocation ladder) {
        try {
            return backend().tierIndex(server, player, community.dimension(), community.villageId(), ladder);
        } catch (Throwable t) {
            return safeZero("tierIndex", t) - 1; // -1 means "unknown"
        }
    }

    public static Map<Integer, Integer> villageScores(MinecraftServer server, UUID player,
                                                      ResourceLocation dimension) {
        try {
            return backend().villageScores(server, player, dimension);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] villageScores failed; returning empty", t);
            return Map.of();
        }
    }

    /** Every village in the overworld this player has standing with — what the FTB tasks ask for. */
    public static Map<Integer, Integer> overworldVillageScores(MinecraftServer server, UUID player) {
        return villageScores(server, player, server.overworld().dimension().location());
    }

    public static Optional<String> tierHighWater(MinecraftServer server, UUID player, Community community,
                                                 ResourceLocation ladder) {
        try {
            return backend().tierHighWater(server, player, community.dimension(), community.villageId(),
                    ladder);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] tierHighWater failed; returning empty", t);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /**
     * Records a reputation outcome.
     *
     * <p>Every failure is contained: a broken backend must not be able to block a quest turn-in or a
     * project completion, because the player has already done the work (§29.3).
     *
     * @return the player's resulting score, or {@code 0} if nothing could be applied
     */
    public static int award(ReputationAward award) {
        if (award.delta() == 0 && award.incidentType() == null) {
            return 0; // nothing to record and nothing to say
        }
        try {
            return backend().award(award);
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] a reputation award failed for {}; the quest outcome "
                    + "itself is unaffected", award.player(), t);
            return 0;
        }
    }

    /** Convenience for the common "this player did this for this village" case. */
    public static int award(MinecraftServer server, UUID player, Community community, int delta,
                            @Nullable ResourceLocation incident, @Nullable String dedupeKey,
                            @Nullable String sourceTitle) {
        ReputationAward.Builder builder = ReputationAward
                .builder(server, player, community.dimension(), community.villageId(), SOURCE)
                .delta(delta)
                .incident(incident)
                .dedupeKey(dedupeKey);
        if (sourceTitle != null) {
            builder.context("source_title", sourceTitle);
        }
        return award(builder.build());
    }

    public static boolean grantTitle(MinecraftServer server, UUID player, @Nullable Community community,
                                     ResourceLocation title, boolean global) {
        try {
            return backend().grantTitle(server, player,
                    community == null ? null : community.dimension(),
                    community == null ? 0 : community.villageId(), title, global);
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] granting title {} failed", title, t);
            return false;
        }
    }

    public static boolean hasTitle(MinecraftServer server, UUID player, @Nullable Community community,
                                   ResourceLocation title, boolean global) {
        try {
            return backend().hasTitle(server, player,
                    community == null ? null : community.dimension(),
                    community == null ? 0 : community.villageId(), title, global);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] hasTitle failed; answering false", t);
            return false;
        }
    }

    public static Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
        try {
            return backend().globalTitles(server, player);
        } catch (Throwable t) {
            return Set.of();
        }
    }

    public static Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player,
                                                      Community community) {
        try {
            return backend().villageTitles(server, player, community.dimension(), community.villageId());
        } catch (Throwable t) {
            return Set.of();
        }
    }

    // ------------------------------------------------------------------
    // Incidents
    // ------------------------------------------------------------------

    public static boolean hasIncident(MinecraftServer server, UUID player, Community community,
                                      IncidentSelector selector) {
        try {
            return backend().hasIncident(server, player, community.dimension(), community.villageId(),
                    selector);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] hasIncident failed; answering false", t);
            return false;
        }
    }

    public static boolean resolveIncident(MinecraftServer server, UUID player, Community community,
                                          IncidentSelector selector, String resolution,
                                          @Nullable String dedupeKey) {
        try {
            return backend().resolveIncident(server, player, community.dimension(), community.villageId(),
                    selector, resolution, dedupeKey);
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] resolving an incident failed; the quest reward itself "
                    + "is unaffected", t);
            return false;
        }
    }

    public static boolean recordIncident(ReputationAward award) {
        try {
            return backend().recordIncident(award);
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] recording an incident failed", t);
            return false;
        }
    }

    private static int safeZero(String what, Throwable t) {
        McaQuests.LOGGER.debug("[MCA: Quests] {} failed; defaulting to 0", what, t);
        return 0;
    }
}
