package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import dev.otectus.mcaquests.quest.reward.HeartsWithParticipantsReward;
import dev.otectus.mcaquests.quest.reward.HeartsWithSponsorReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.UnlockReward;
import dev.otectus.mcaquests.quest.reward.VillageReputationReward;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
                    data.addPending(pid, new PendingReward(def.id(), phaseIndex, ri));
                }
            }
        }
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
