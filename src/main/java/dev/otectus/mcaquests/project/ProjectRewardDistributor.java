package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import dev.otectus.mcaquests.quest.reward.HeartsWithParticipantsReward;
import dev.otectus.mcaquests.quest.reward.HeartsWithSponsorReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.UnlockReward;
import dev.otectus.mcaquests.quest.reward.VillageReputationReward;
import dev.otectus.mcaquests.quest.title.TitleService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Distributes a completed phase's rewards to the right recipients (spec 0.4.0). Reuses the existing
 * {@link QuestReward} types via {@code grant(player, sponsor)} for normal rewards, and special-cases the
 * project rewards that need scope/participant context. Online players are paid immediately; offline
 * players have non-hearts player rewards queued for login delivery; unloaded villagers have hearts
 * queued via MCA. Called exactly once per phase (guarded by {@code ProjectState.tryMarkPhaseDistributed}).
 */
public final class ProjectRewardDistributor {

    private ProjectRewardDistributor() {
    }

    public static void distribute(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                  ProjectState state, ProjectDefinition def, int phaseIndex) {
        ProjectPhase phase = def.phase(phaseIndex);
        List<SharedReward> rewards = phase.rewards();
        Entity sponsor = ProjectManager.resolveSponsor(server, state);
        Set<UUID> phaseContributors = state.currentPhaseContributors();
        UUID top = topContributor(state);

        for (int ri = 0; ri < rewards.size(); ri++) {
            SharedReward shared = rewards.get(ri);
            QuestReward reward = shared.reward();

            if (reward instanceof UnlockReward unlock) {
                ProjectManager.seedFollowUp(server, level, data, state, unlock.target());
                continue;
            }
            if (reward instanceof VillageReputationReward rep) {
                dev.otectus.mcaquests.quest.reputation.ReputationService.award(
                        data, server, state.identity(), rep.amount(), null);
                continue;
            }

            Collection<UUID> recipients = recipientsFor(shared.target(), state, phaseContributors, top);
            for (UUID pid : recipients) {
                ServerPlayer player = server.getPlayerList().getPlayer(pid);
                if (player != null) {
                    grantPlayerReward(level, state, player, reward, sponsor);
                } else if (canQueueOffline(reward)) {
                    data.addPending(pid, PendingReward.ofPhase(def.id(), phaseIndex, ri));
                }
            }
        }
    }

    private static final int VILLAGE_RESOLUTION_RADIUS = 128; // matches mcaquests:village_reputation §16.1
    private static final double VILLAGER_RESOLUTION_RADIUS = 16; // matches mcaquests:hearts §16.2

    /**
     * Retries a banked FTB-claim reward (spec 1.0.0 §16, task M3.1) against the player's current
     * surroundings — called from {@code ProjectManager.deliverPending} on login and once per in-game day
     * while online. Returns {@code true} once a target resolved and the reward was delivered (safe to
     * drop from the pending list); {@code false} if it is still undeliverable (stays banked, retried
     * again next time). Every McaCompat lookup used below already defaults safely on its own failure;
     * this method's own catch exists only so an unexpected exception here can never escape into the
     * caller's login/tick path — it degrades to "still banked" instead.
     */
    public static boolean attemptBankedDelivery(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                                BankedReward reward) {
        try {
            return switch (reward.type()) {
                case REPUTATION -> deliverBankedReputation(server, level, player, reward);
                case HEARTS -> deliverBankedHearts(level, player, reward);
                case TITLE -> deliverBankedTitle(level, player, reward);
            };
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean deliverBankedReputation(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                                   BankedReward reward) {
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
        if (villageId.isEmpty()) {
            return false;
        }
        ReputationService.award(server, "v:" + villageId.getAsInt(), reward.amount(), player);
        return true;
    }

    private static boolean deliverBankedHearts(ServerLevel level, ServerPlayer player, BankedReward reward) {
        return switch (reward.target()) {
            case "SPOUSE" -> deliverSpouseHearts(player, reward.amount());
            case "VILLAGE_RESIDENTS" -> deliverVillageResidentHearts(level, player, reward.amount());
            default -> deliverNearestVillagerHearts(player, reward.amount()); // NEAREST_VILLAGER + unknown fallback
        };
    }

    /**
     * Reuses the same "best hearts nearby, then confirm it's the spouse" idiom the FTBQ {@code hearts}
     * task's {@code spouse_only} mode already relies on (see {@link McaCompat#bestHeartsVillagerWithin}).
     */
    private static boolean deliverSpouseHearts(ServerPlayer player, int amount) {
        Optional<Entity> candidate = McaCompat.bestHeartsVillagerWithin(player, VILLAGER_RESOLUTION_RADIUS);
        if (candidate.isEmpty() || !McaCompat.isPlayerSpouse(player, candidate.get())) {
            return false;
        }
        McaCompat.addHearts(player, candidate.get(), amount);
        return true;
    }

    /**
     * {@link McaCompat} exposes no plain nearest-by-distance villager lookup, only the hearts-ranked
     * scan used for spouse resolution; reused here too (best-hearts-nearby as a stand-in for "the
     * nearest loaded adult villager") rather than adding a new McaCompat surface for this task.
     */
    private static boolean deliverNearestVillagerHearts(ServerPlayer player, int amount) {
        Optional<Entity> candidate = McaCompat.bestHeartsVillagerWithin(player, VILLAGER_RESOLUTION_RADIUS);
        if (candidate.isEmpty()) {
            return false;
        }
        McaCompat.addHearts(player, candidate.get(), amount);
        return true;
    }

    /** Mirrors {@link #grantParticipantHearts}'s live-then-push pattern for every resident of the nearest village. */
    private static boolean deliverVillageResidentHearts(ServerLevel level, ServerPlayer player, int amount) {
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
        if (villageId.isEmpty()) {
            return false;
        }
        int id = villageId.getAsInt();
        for (UUID residentUuid : McaCompat.villageResidentUuids(level, id)) {
            Entity resident = level.getEntity(residentUuid);
            if (resident != null && resident.isAlive() && McaCompat.isMcaVillager(resident)) {
                McaCompat.addHearts(player, resident, amount);
            } else {
                McaCompat.pushVillageHearts(level, id, residentUuid, amount);
            }
        }
        return true;
    }

    private static boolean deliverBankedTitle(ServerLevel level, ServerPlayer player, BankedReward reward) {
        if (!"VILLAGE".equals(reward.titleScope())) {
            TitleService.grantGlobal(player, reward.titleId());
            return true;
        }
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
        if (villageId.isEmpty()) {
            return false;
        }
        TitleService.grantVillage(player, villageId.getAsInt(), reward.titleId());
        return true;
    }

    /** Grants the {@code rewardIndex} reward of {@code phase} to a returning player (login delivery). */
    public static void grantPending(ServerLevel level, ProjectState state, ServerPlayer player,
                                    ProjectDefinition def, int phase, int rewardIndex) {
        if (phase < 0 || phase >= def.phaseCount()) {
            return;
        }
        List<SharedReward> rewards = def.phase(phase).rewards();
        if (rewardIndex < 0 || rewardIndex >= rewards.size()) {
            return;
        }
        Entity sponsor = ProjectManager.resolveSponsor(player.getServer(), state);
        grantPlayerReward(level, state, player, rewards.get(rewardIndex).reward(), sponsor);
    }

    private static Collection<UUID> recipientsFor(SharedRewardTarget target, ProjectState state,
                                                  Set<UUID> phaseContributors, @Nullable UUID top) {
        return switch (target) {
            case CONTRIBUTORS -> phaseContributors;
            case ALL_PARTICIPANTS -> state.participants();
            case TOP_CONTRIBUTOR -> top == null ? List.of() : List.of(top);
            case SPONSOR_VILLAGE -> List.of(); // village-level rewards (reputation) handled above
        };
    }

    private static void grantPlayerReward(ServerLevel level, ProjectState state, ServerPlayer player,
                                          QuestReward reward, @Nullable Entity sponsor) {
        if (reward instanceof HeartsWithParticipantsReward hearts) {
            grantParticipantHearts(level, state, player, hearts);
        } else {
            reward.grant(player, sponsor);
        }
    }

    private static void grantParticipantHearts(ServerLevel level, ProjectState state, ServerPlayer player,
                                               HeartsWithParticipantsReward reward) {
        int amount = reward.effectiveAmount();
        Set<UUID> villagers = new LinkedHashSet<>(state.sponsors());
        if (reward.includeResidents() && state.villageId().isPresent()) {
            villagers.addAll(McaCompat.villageResidentUuids(level, state.villageId().getAsInt()));
        }
        for (UUID villagerUuid : villagers) {
            Entity villager = level.getEntity(villagerUuid);
            if (villager != null && villager.isAlive() && McaCompat.isMcaVillager(villager)) {
                McaCompat.addHearts(player, villager, amount);
            } else if (state.villageId().isPresent()) {
                McaCompat.pushVillageHearts(level, state.villageId().getAsInt(), villagerUuid, amount);
            }
        }
    }

    private static boolean canQueueOffline(QuestReward reward) {
        return !(reward instanceof HeartsReward
                || reward instanceof HeartsWithSponsorReward
                || reward instanceof HeartsWithParticipantsReward);
    }

    @Nullable
    private static UUID topContributor(ProjectState state) {
        Map<UUID, Integer> totals = new java.util.HashMap<>();
        for (SharedObjectiveProgress progress : state.progress()) {
            progress.contributions().forEach((uuid, amount) -> totals.merge(uuid, amount, Integer::sum));
        }
        UUID best = null;
        int bestAmount = 0;
        for (Map.Entry<UUID, Integer> entry : totals.entrySet()) {
            if (entry.getValue() > bestAmount) {
                bestAmount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
