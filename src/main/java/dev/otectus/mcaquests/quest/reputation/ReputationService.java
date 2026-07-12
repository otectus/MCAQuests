package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.api.event.ReputationTierReachedEvent;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The single funnel for all village-reputation writes (spec 0.7.0). Every quest and project reward that
 * moves reputation routes through {@link #award} so that crossing a tier threshold is detected exactly
 * once (guarded by the high-water mark in {@link ProjectSavedData}) and the consequences — granting a
 * tier's title and sending a tier-up toast — happen consistently. Tier-up logic only applies to
 * per-village reputation (identities of the form {@code "v:<id>"}); other scope identities just accrue.
 *
 * <p>Null-player safe: project rewards can move reputation with no online player. In that case the
 * high-water mark and any title grant still apply (the title is applied to the player on next login via
 * the journal sync path); the toast is simply skipped.
 */
public final class ReputationService {

    private ReputationService() {
    }

    public static int award(MinecraftServer server, String identity, int delta, @Nullable ServerPlayer player) {
        return award(ProjectSavedData.get(server), server, identity, delta, player);
    }

    public static int award(ProjectSavedData data, @Nullable MinecraftServer server,
                            String identity, int delta, @Nullable ServerPlayer player) {
        int oldRep = data.reputation(identity);
        data.addReputation(identity, delta);
        int newRep = data.reputation(identity);

        OptionalInt villageId = parseVillageId(identity);
        if (villageId.isEmpty()) {
            return newRep; // tier-up is only meaningful for per-village reputation
        }

        ReputationTierSet ladder = ReputationTiers.getDefault();
        Optional<ReputationTier> reached = tierUpReached(ladder, oldRep, newRep, data.tierHighWater(identity));
        if (reached.isPresent()) {
            data.setTierHighWater(identity, reached.get().id());
            onTierUp(server, player, villageId.getAsInt(), reached.get());
            MinecraftForge.EVENT_BUS.post(new ReputationTierReachedEvent(player, villageId.getAsInt(),
                    ReputationTiers.DEFAULT_ID, reached.get().id(), ladder.indexOf(reached.get().id())));
        }
        return newRep;
    }

    /**
     * Pure decision: given a ladder, the reputation before/after a change, and the highest tier id ever
     * reached for this identity ({@code null} if none), returns the newly-reached tier when the change
     * crosses strictly above both the old tier and the high-water mark. Empty otherwise. Extracted for
     * unit testing.
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
     * Pure decision: given a ladder and a reputation value, returns the index of the highest tier
     * whose threshold is ≤ reputation; returns -1 if reputation is below the lowest tier's threshold
     * or if the ladder is empty. Extracted for unit testing.
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

    /** Queries the village reputation for a given village id; empty when saved data absent or no entry. */
    public static OptionalInt villageReputation(MinecraftServer server, int villageId) {
        ProjectSavedData data = ProjectSavedData.get(server);
        int rep = data.reputation("v:" + villageId);
        return rep == 0 && !data.reputationKeys().contains("v:" + villageId) ? OptionalInt.empty() : OptionalInt.of(rep);
    }

    /**
     * Queries all village reputations from saved data. Only includes "v:<id>" identity keys,
     * parsed to integer village ids; other identity kinds are excluded. Returns an empty map
     * when saved data is absent or contains no village reputations. The returned map is a copy
     * and does not expose internal mutable state.
     */
    public static Map<Integer, Integer> allVillageReputations(MinecraftServer server) {
        ProjectSavedData data = ProjectSavedData.get(server);
        Map<Integer, Integer> result = new HashMap<>();
        for (String key : data.reputationKeys()) {
            OptionalInt villageId = parseVillageId(key);
            if (villageId.isPresent()) {
                result.put(villageId.getAsInt(), data.reputation(key));
            }
        }
        return result;
    }

    /**
     * Resolves the current tier for a village's reputation against a specified ladder.
     * Returns empty when saved data is absent, no entry for the village, or the ladder cannot
     * be resolved. The tier index is calculated using {@link #tierIndex(ReputationTierSet, int)};
     * an index of -1 (reputation below lowest threshold) returns empty.
     */
    public static Optional<ReputationTier> currentTier(MinecraftServer server, int villageId, ResourceLocation ladder) {
        ReputationTierSet tiers = ReputationTiers.get(ladder).orElse(null);
        if (tiers == null) {
            return Optional.empty();
        }
        OptionalInt rep = villageReputation(server, villageId);
        if (rep.isEmpty()) {
            return Optional.empty();
        }
        int index = tierIndex(tiers, rep.getAsInt());
        return index < 0 ? Optional.empty() : Optional.of(tiers.tiers().get(index));
    }

    /** Parses {@code "v:<id>"} village identities; empty for any other scope key. */
    public static OptionalInt parseVillageId(String identity) {
        if (identity.startsWith("v:")) {
            try {
                return OptionalInt.of(Integer.parseInt(identity.substring(2)));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return OptionalInt.empty();
    }

    private static void onTierUp(@Nullable MinecraftServer server, @Nullable ServerPlayer player,
                                 int villageId, ReputationTier reached) {
        // Grant the tier's title (village-scoped) to the player who pushed reputation over the threshold.
        // Offline project-driven tier-ups have no player to award; the high-water mark still advances.
        if (player != null) {
            reached.grantsTitle().ifPresent(title ->
                    dev.otectus.mcaquests.quest.title.TitleService.grantVillage(player, villageId, title));
            dev.otectus.mcaquests.network.QuestNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new dev.otectus.mcaquests.network.ReputationTierToastS2CPacket(
                            net.minecraft.network.chat.Component.literal(reached.name())));
        }
    }
}
