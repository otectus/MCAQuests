package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.project.state.ProjectSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
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
