package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ReputationAward;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The pre-1.1.0 reputation entry point, retained as a thin, <b>deprecated</b> shim over
 * {@link QuestReputation}.
 *
 * <h2>What happened to it</h2>
 *
 * <p>This class used to be the funnel: it read and wrote {@code ProjectSavedData}'s shared
 * {@code "v:<id>"} map, detected tier crossings, and granted titles. Two problems made that
 * untenable. The map was <em>world-shared</em>, so on a server every player read the same number for a
 * village; and it was <em>dimension-blind</em>, so village 3 in the Nether and village 3 in the
 * overworld collided. §29 replaces it with {@link QuestReputation}, which resolves a dimension-aware
 * community and delegates to whichever backend is live.
 *
 * <p>Everything here still works, and other mods that call it keep working. But the signatures cannot
 * express a player or a dimension, so they have to guess at both: the overworld, and whichever player
 * was passed (if any). New code — inside this mod or outside it — should call {@link QuestReputation}
 * and pass a real {@code Community}.
 *
 * <p>The pure tier helpers are unchanged and undeprecated; they never touched storage.
 */
@Deprecated
public final class ReputationService {

    private ReputationService() {
    }

    /**
     * Applies a delta to a legacy {@code "v:<id>"} scope identity.
     *
     * @param player the player who earned it. <b>Required.</b> The old signature allowed null for
     *               project rewards with nobody online, which is exactly how a world-shared score came
     *               about; a null player now logs and no-ops rather than moving a number that belongs
     *               to nobody.
     * @return the player's resulting score, or 0
     * @deprecated use {@link QuestReputation#award(ReputationAward)} with a dimension-aware community.
     */
    @Deprecated
    public static int award(MinecraftServer server, String identity, int delta,
                            @Nullable ServerPlayer player) {
        OptionalInt villageId = parseVillageId(identity);
        if (villageId.isEmpty()) {
            return 0; // tier-up and standing were only ever meaningful for village identities
        }
        if (player == null) {
            McaQuests.LOGGER.warn("[MCA: Quests] ReputationService.award was called for {} with no player. "
                    + "Standing is per player from 1.1.0 onward, so there is nobody to award; use "
                    + "QuestReputation with an explicit recipient instead.", identity);
            return 0;
        }
        QuestReputation.Community community = QuestReputation.inLevel(server.overworld(),
                villageId.getAsInt());
        return QuestReputation.award(ReputationAward
                .builder(server, player.getUUID(), community.dimension(), community.villageId(),
                        QuestReputation.SOURCE)
                .delta(delta)
                .build());
    }

    /**
     * @deprecated the {@code ProjectSavedData} parameter is ignored; the store is chosen by the bridge.
     */
    @Deprecated
    public static int award(dev.otectus.mcaquests.project.state.ProjectSavedData ignored,
                            @Nullable MinecraftServer server, String identity, int delta,
                            @Nullable ServerPlayer player) {
        return server == null ? 0 : award(server, identity, delta, player);
    }

    /**
     * A player's standing with a village in the overworld.
     *
     * @deprecated use {@link QuestReputation#score} with a dimension-aware community.
     */
    @Deprecated
    public static OptionalInt villageReputation(MinecraftServer server, UUID player, int villageId) {
        return OptionalInt.of(QuestReputation.score(server, player,
                QuestReputation.inLevel(server.overworld(), villageId)));
    }

    /**
     * Every overworld village this player has standing with.
     *
     * @deprecated use {@link QuestReputation#overworldVillageScores}.
     */
    @Deprecated
    public static Map<Integer, Integer> allVillageReputations(MinecraftServer server, UUID player) {
        return new HashMap<>(QuestReputation.overworldVillageScores(server, player));
    }

    /**
     * The player's current tier with an overworld village on a named ladder.
     *
     * @deprecated use {@link QuestReputation#tierId}.
     */
    @Deprecated
    public static Optional<ReputationTier> currentTier(MinecraftServer server, UUID player, int villageId,
                                                       ResourceLocation ladder) {
        ReputationTierSet tiers = ReputationTiers.get(ladder).orElse(null);
        if (tiers == null) {
            return Optional.empty();
        }
        int score = QuestReputation.score(server, player, QuestReputation.inLevel(server.overworld(), villageId));
        int index = tierIndex(tiers, score);
        return index < 0 ? Optional.empty() : Optional.of(tiers.tiers().get(index));
    }

    /** Convenience for a level other than the overworld. */
    public static Optional<ReputationTier> currentTier(MinecraftServer server, UUID player, ServerLevel level,
                                                       int villageId, ResourceLocation ladder) {
        ReputationTierSet tiers = ReputationTiers.get(ladder).orElse(null);
        if (tiers == null) {
            return Optional.empty();
        }
        int score = QuestReputation.score(server, player, QuestReputation.inLevel(level, villageId));
        int index = tierIndex(tiers, score);
        return index < 0 ? Optional.empty() : Optional.of(tiers.tiers().get(index));
    }

    // ------------------------------------------------------------------
    // Pure helpers — never touched storage, and are unchanged
    // ------------------------------------------------------------------

    /**
     * Given a ladder, a before/after pair, and the highest tier ever reached, the newly-reached tier
     * when the change crosses strictly above both. Empty otherwise. Extracted for unit testing.
     */
    public static Optional<ReputationTier> tierUpReached(ReputationTierSet ladder, int oldRep, int newRep,
                                                         @Nullable String highWaterTierId) {
        if (ladder.isEmpty() || newRep <= oldRep) {
            return Optional.empty();
        }
        int oldIndex = ladder.indexOf(ladder.tierFor(oldRep).id());
        int newIndex = ladder.indexOf(ladder.tierFor(newRep).id());
        if (newIndex <= oldIndex) {
            return Optional.empty();
        }
        int highWaterIndex = highWaterTierId == null ? -1 : ladder.indexOf(highWaterTierId);
        if (newIndex <= highWaterIndex) {
            return Optional.empty();
        }
        return Optional.of(ladder.tiers().get(newIndex));
    }

    /**
     * The index of the highest tier whose threshold is ≤ {@code reputation}; {@code -1} when the value
     * is below the lowest threshold or the ladder is empty. Extracted for unit testing.
     */
    public static int tierIndex(ReputationTierSet ladder, int reputation) {
        if (ladder.isEmpty()) {
            return -1;
        }
        int index = -1;
        for (int i = 0; i < ladder.tiers().size(); i++) {
            if (reputation >= ladder.tiers().get(i).threshold()) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    /** Parses a legacy {@code "v:<id>"} scope identity; empty for any other scope key. */
    public static OptionalInt parseVillageId(String identity) {
        if (identity != null && identity.startsWith("v:")) {
            try {
                return OptionalInt.of(Integer.parseInt(identity.substring(2)));
            } catch (NumberFormatException ignored) {
                // not a village identity
            }
        }
        return OptionalInt.empty();
    }
}
