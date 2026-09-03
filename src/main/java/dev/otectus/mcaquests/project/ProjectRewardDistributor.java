package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import dev.otectus.mcaquests.quest.reward.CurrencyReward;
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
                // The legacy shared reward, reinterpreted per §29.4: `sponsor_village` used to mean
                // "add this to the village's one number", which under per-player standing can only
                // sensibly mean "credit everyone who helped". Other shared targets keep their own
                // recipient sets, which is why the target is consulted rather than assumed.
                var outcome = dev.otectus.mcaquests.quest.reputation.ReputationOutcome
                        .ofShorthand(rep.amount())
                        .withDefaultRecipients(recipientsKindFor(shared.target()))
                        .withDefaultIncident(dev.otectus.mcaquests.quest.reputation
                                .QuestReputationBlock.Incidents.PROJECT_PHASE_COMPLETED);
                ProjectReputation.apply(server, level, state, def, outcome, "phase", phaseIndex);
                continue;
            }

            // Roll a randomized reward once, here, before any recipient is paid, so everyone who helped —
            // including anyone offline who collects it later — receives the same amount.
            if (reward instanceof CurrencyReward currency) {
                state.freezeReward(phaseIndex, ri, currency.roll(level.getRandom()));
            }

            Collection<UUID> recipients = recipientsFor(shared.target(), state, phaseContributors, top);
            for (UUID pid : recipients) {
                ServerPlayer player = server.getPlayerList().getPlayer(pid);
                if (player != null) {
                    grantPlayerReward(level, state, player, reward, sponsor, state.frozenReward(phaseIndex, ri));
                } else if (canQueueOffline(reward)) {
                    data.addPending(pid, PendingReward.ofPhase(def.id(), phaseIndex, ri));
                }
            }
        }
    }

    /**
     * Maps a shared-reward target onto a reputation recipient set (§29.4).
     *
     * <p>{@code sponsor_village} is the interesting case. Under the old shared score it meant "add
     * this to the village's single number", which per-player standing simply cannot express. §29.4
     * directs it to be read as "everyone who took part", which is the closest thing to what the
     * author meant, and warns once so they can state it explicitly.
     */
    private static dev.otectus.mcaquests.quest.reputation.ReputationOutcome.Recipients recipientsKindFor(
            SharedRewardTarget target) {
        return switch (target) {
            case CONTRIBUTORS, TOP_CONTRIBUTOR ->
                    dev.otectus.mcaquests.quest.reputation.ReputationOutcome.Recipients.PHASE_CONTRIBUTORS;
            case ALL_PARTICIPANTS ->
                    dev.otectus.mcaquests.quest.reputation.ReputationOutcome.Recipients.ALL_PARTICIPANTS;
            case SPONSOR_VILLAGE -> {
                if (!sponsorVillageWarned) {
                    sponsorVillageWarned = true;
                    dev.otectus.mcaquests.McaQuests.LOGGER.warn(
                            "[MCA: Quests] a project reward targets 'sponsor_village' with "
                            + "village_reputation. Village standing is per player from 1.1.0, so it is "
                            + "credited to every participant instead. State an explicit \"recipients\" "
                            + "in the project's reputation block to be unambiguous.");
                }
                yield dev.otectus.mcaquests.quest.reputation.ReputationOutcome.Recipients.ALL_PARTICIPANTS;
            }
        };
    }

    /** One warning per session is enough to be useful without being noise. */
    private static boolean sponsorVillageWarned;

    private static final int VILLAGE_RESOLUTION_RADIUS = 128; // matches mcaquests:village_reputation §16.1
    private static final double VILLAGER_RESOLUTION_RADIUS = 16; // matches mcaquests:hearts §16.2

    /**
     * Retries a banked FTB-claim reward (spec 1.0.0 §16, task M3.1) against the player's current
     * surroundings — called from {@code ProjectManager.deliverPending} on login and once per in-game day
     * while online. Returns {@code true} once a target resolved and the reward was delivered (safe to
     * drop from the pending list); {@code false} if it is still undeliverable (stays banked, retried
     * again next time). Fail-safe per §10.2: any {@link Throwable} is logged at DEBUG and degrades to
     * the documented default ({@code false} — "still banked"), so a failure here can never escape into
     * the caller's login/tick path.
     */
    public static boolean attemptBankedDelivery(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                                BankedReward reward) {
        try {
            return switch (reward.type()) {
                case REPUTATION -> deliverBankedReputation(server, level, player, reward);
                case HEARTS -> deliverBankedHearts(level, player, reward);
                case TITLE -> deliverBankedTitle(level, player, reward);
            };
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] banked {} delivery failed for {}; staying banked",
                    reward.type(), player.getGameProfile().getName(), t);
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

    /**
     * Re-derives the multiplier/clamp at delivery time (task M3.2 finding: the configured
     * {@code heartsRewardMultiplier}/min/max clamp lives in {@link HeartsReward#effectiveAmount()}, not
     * in {@link McaCompat#addHearts}) rather than banking the already-clamped value, so a config change
     * between claim and delivery is honoured exactly like every other hearts path.
     */
    private static boolean deliverBankedHearts(ServerLevel level, ServerPlayer player, BankedReward reward) {
        int amount = new HeartsReward(reward.amount()).effectiveAmount();
        return switch (reward.target()) {
            case "SPOUSE" -> deliverSpouseHearts(player, amount);
            case "VILLAGE_RESIDENTS" -> deliverVillageResidentHearts(level, player, amount);
            default -> deliverNearestVillagerHearts(player, amount); // NEAREST_VILLAGER + unknown fallback
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
     * Genuinely nearest-by-distance among <em>adult</em> MCA villagers (spec §16.2: "nearest loaded adult
     * MCA villager"), via {@link McaCompat#nearestAdultVillagerWithin} (task M3.2) — aligned with the
     * {@code mcaquests:hearts} reward's own {@code NEAREST_VILLAGER} claim path so claim-now and
     * deliver-later agree on what "nearest" means.
     */
    private static boolean deliverNearestVillagerHearts(ServerPlayer player, int amount) {
        Optional<Entity> candidate = McaCompat.nearestAdultVillagerWithin(player, VILLAGER_RESOLUTION_RADIUS);
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
            McaCompat.awardHearts(level, residentUuid, player, amount);
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
        // Reads the amount frozen when the phase was distributed, so a player who was offline then is paid
        // exactly what everyone else was — never a fresh roll on login.
        grantPlayerReward(level, state, player, rewards.get(rewardIndex).reward(), sponsor,
                state.frozenReward(phase, rewardIndex));
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
                                          QuestReward reward, @Nullable Entity sponsor, OptionalInt frozenAmount) {
        if (reward instanceof HeartsWithParticipantsReward hearts) {
            grantParticipantHearts(level, state, player, hearts);
        } else if (reward instanceof HeartsWithSponsorReward hearts) {
            grantSponsorHearts(level, state, player, hearts, sponsor);
        } else if (reward instanceof CurrencyReward currency && frozenAmount.isPresent()) {
            currency.grantAmount(player, frozenAmount.getAsInt());
        } else {
            reward.grant(player, sponsor);
        }
    }

    /**
     * Pays the sponsor hearts, and pays them <em>somewhere</em> when the sponsor is gone.
     *
     * <p>A project whose sponsor died before the phase completed — exactly the case {@code sponsor.on_death}
     * turn-to-village exists for — resolved to a null villager, and {@code HeartsWithSponsorReward.grant}
     * no-ops on null: the reward was silently dropped. It now falls back the way the other two hearts
     * rewards do, banking against every villager who ever sponsored this instance (so an unloaded one is
     * queued rather than lost, via {@code PendingHeartsData}).
     */
    private static void grantSponsorHearts(ServerLevel level, ProjectState state, ServerPlayer player,
                                           HeartsWithSponsorReward reward, @Nullable Entity sponsor) {
        if (sponsor != null) {
            reward.grant(player, sponsor);
            return;
        }
        Set<UUID> sponsors = new LinkedHashSet<>(state.sponsors());
        if (sponsors.isEmpty()) {
            McaQuests.LOGGER.debug("[MCA: Quests] project '{}' pays hearts_with_sponsor with no sponsor "
                    + "recorded at all; nothing to credit", state.projectId());
            return;
        }
        McaQuests.LOGGER.debug("[MCA: Quests] project '{}' has no loaded sponsor; banking "
                + "hearts_with_sponsor against {} recorded sponsor(s)", state.projectId(), sponsors.size());
        int amount = reward.effectiveAmount();
        for (UUID sponsorUuid : sponsors) {
            McaCompat.awardHearts(level, sponsorUuid, player, amount);
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
            McaCompat.awardHearts(level, villagerUuid, player, amount);
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
