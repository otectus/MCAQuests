package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.ReputationTierReachedEvent;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.state.QuestCapabilities;
import dev.otectus.mcaquests.state.VillageStanding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quests' own reputation implementation, used when MCA: Reputation is absent (spec §29.2).
 *
 * <p>This is the behaviour a Quests-only installation gets, and it must stay <b>exactly as playable
 * as before</b>: the same tier ladders, the same titles, the same toasts, the same
 * {@link ReputationTierReachedEvent}, the same datapack fields. The one deliberate change is the one
 * §29.2 asks for — the store underneath is now per player and dimension-aware, so on a multiplayer
 * server two players in the same village finally have their own standing instead of sharing one
 * number.
 *
 * <p>There is no incident ledger here, and inventing a shallow one would be worse than not having it:
 * the incident queries answer honestly that they know nothing, so a quest gated on "you assaulted
 * somebody here" simply never offers itself rather than offering itself unconditionally (§35.1).
 */
public final class LegacyReputationBackend implements ReputationBackend {

    @Override
    public boolean isCanonical() {
        return false;
    }

    @Override
    public String backendName() {
        return "mcaquests:builtin";
    }

    private static VillageStanding standing(MinecraftServer server) {
        return ProjectSavedData.get(server).standing();
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Override
    public int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
        return standing(server).score(player, dimension, villageId);
    }

    @Override
    public String tierId(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                         ResourceLocation ladder) {
        return ReputationTiers.getOrDefault(ladder)
                .tierFor(score(server, player, dimension, villageId)).id();
    }

    @Override
    public int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                         ResourceLocation ladder) {
        ReputationTierSet set = ReputationTiers.getOrDefault(ladder);
        return set.indexOf(set.tierFor(score(server, player, dimension, villageId)).id());
    }

    @Override
    public Map<Integer, Integer> villageScores(MinecraftServer server, UUID player,
                                               ResourceLocation dimension) {
        return standing(server).villageScores(player, dimension);
    }

    @Override
    public Optional<String> tierHighWater(MinecraftServer server, UUID player, ResourceLocation dimension,
                                          int villageId, ResourceLocation ladder) {
        return standing(server).tierHighWater(player, ladder, dimension, villageId);
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /**
     * Applies a delta and runs the tier-up consequences exactly once.
     *
     * <p>Dedupe is honoured by recording applied keys in the standing store's bounded per-player award
     * ring, which survives save/load just as the canonical backend's guarantee does. Until 1.5.1 the
     * keys went into the migration-marker map instead, where nothing ever removed them: one permanent
     * entry per turn-in, situation, project phase and FTB claim, in the world save, forever.
     */
    @Override
    public int award(ReputationAward award) {
        MinecraftServer server = award.server();
        ProjectSavedData data = ProjectSavedData.get(server);
        VillageStanding standing = data.standing();

        if (award.dedupeKey() != null && !award.dedupeKey().isBlank()) {
            String marker = VillageStanding.communityKey(award.dimension(), award.villageId())
                    + ":" + award.dedupeKey();
            if (!standing.recordAward(award.player(), marker)) {
                return standing.score(award.player(), award.dimension(), award.villageId());
            }
        }

        int oldScore = standing.score(award.player(), award.dimension(), award.villageId());
        int newScore = standing.addScore(award.player(), award.dimension(), award.villageId(),
                award.delta());
        data.standingChanged();
        if (newScore == oldScore) {
            return newScore;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(award.player());
        applyTierUp(server, data, award, player, oldScore, newScore);
        return newScore;
    }

    /**
     * The tier-up path Quests has always had: detect a strictly-upward crossing that also beats the
     * high-water mark, advance the mark, grant the tier's title, send the toast, and post
     * {@link ReputationTierReachedEvent}. The high-water guard is what stops a player oscillating
     * around a threshold from re-earning the milestone.
     */
    private void applyTierUp(MinecraftServer server, ProjectSavedData data, ReputationAward award,
                             @Nullable ServerPlayer player, int oldScore, int newScore) {
        if (!McaQuestsConfig.COMMON.enableReputationTiers.get() || newScore <= oldScore) {
            return;
        }
        ReputationTierSet ladder = ReputationTiers.getDefault();
        if (ladder.isEmpty()) {
            return;
        }
        VillageStanding standing = data.standing();
        String highWater = standing
                .tierHighWater(award.player(), ReputationTiers.DEFAULT_ID, award.dimension(),
                        award.villageId())
                .orElse(null);

        int oldIndex = ladder.indexOf(ladder.tierFor(oldScore).id());
        int newIndex = ladder.indexOf(ladder.tierFor(newScore).id());
        int highWaterIndex = highWater == null ? -1 : ladder.indexOf(highWater);
        if (newIndex <= oldIndex || newIndex <= highWaterIndex) {
            return;
        }
        ReputationTier reached = ladder.tiers().get(newIndex);
        standing.setTierHighWater(award.player(), ReputationTiers.DEFAULT_ID, award.dimension(),
                award.villageId(), reached.id());
        data.standingChanged();

        if (player != null) {
            reached.grantsTitle().ifPresent(title ->
                    grantTitle(server, award.player(), award.dimension(), award.villageId(), title, false));
            try {
                dev.otectus.mcaquests.network.QuestNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new dev.otectus.mcaquests.network.ReputationTierToastS2CPacket(
                                net.minecraft.network.chat.Component.literal(reached.name())));
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("[MCA: Quests] tier toast delivery failed; standing is unaffected", t);
            }
        }
        MinecraftForge.EVENT_BUS.post(new ReputationTierReachedEvent(player, award.villageId(),
                ReputationTiers.DEFAULT_ID, reached.id(), newIndex));
    }

    @Override
    public boolean grantTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                              int villageId, ResourceLocation title, boolean global) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (global) {
            return online != null
                    && dev.otectus.mcaquests.quest.title.TitleService.grantGlobal(online, title);
        }
        if (dimension == null) {
            return false;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        // The dimension-aware store is the authority and works offline; PlayerTitles is written too so
        // the existing Journal, FTB, and title-chain paths keep seeing what they always have.
        boolean added = data.standing().grantVillageTitle(player, dimension, villageId, title);
        if (added) {
            data.standingChanged();
        }
        if (online != null) {
            boolean legacyAdded =
                    dev.otectus.mcaquests.quest.title.TitleService.grantVillage(online, villageId, title);
            return added || legacyAdded;
        }
        return added;
    }

    @Override
    public boolean hasTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                            int villageId, ResourceLocation title, boolean global) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (global) {
            return online != null && QuestCapabilities.get(online)
                    .map(data -> data.titles().hasGlobal(title)).orElse(false);
        }
        if (dimension != null
                && ProjectSavedData.get(server).standing().hasVillageTitle(player, dimension, villageId, title)) {
            return true;
        }
        return online != null && QuestCapabilities.get(online)
                .map(data -> data.titles().hasVillage(dimension, villageId, title)).orElse(false);
    }

    @Override
    public Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online == null ? Set.of() : QuestCapabilities.get(online)
                .<Set<ResourceLocation>>map(data -> new LinkedHashSet<>(data.titles().global()))
                .orElseGet(Set::of);
    }

    @Override
    public Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player,
                                               ResourceLocation dimension, int villageId) {
        Set<ResourceLocation> held =
                new LinkedHashSet<>(ProjectSavedData.get(server).standing()
                        .villageTitles(player, dimension, villageId));
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            QuestCapabilities.get(online)
                    .ifPresent(data -> held.addAll(data.titles().forVillage(dimension, villageId)));
        }
        return held;
    }

    // ------------------------------------------------------------------
    // Incidents — honestly unsupported
    // ------------------------------------------------------------------

    @Override
    public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                               int villageId, IncidentSelector selector) {
        return false;
    }

    @Override
    public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector, String resolution,
                                   @Nullable String dedupeKey) {
        return false;
    }

    @Override
    public boolean recordIncident(ReputationAward award) {
        // Without a ledger there is nothing to record, but a caller-supplied delta is still standing
        // and must not be silently dropped — that would make a datapack behave differently depending
        // on which mods are installed for no reason the author can see.
        return award.delta() != 0 && award(award) != 0;
    }
}
