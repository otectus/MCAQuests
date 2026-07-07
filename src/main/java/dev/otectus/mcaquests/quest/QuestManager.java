package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.api.ExternalSignalObjective;
import dev.otectus.mcaquests.api.QuestDialogueHooks;
import dev.otectus.mcaquests.api.event.QuestAbandonedEvent;
import dev.otectus.mcaquests.api.event.QuestAcceptedEvent;
import dev.otectus.mcaquests.api.event.QuestCompletedEvent;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.api.event.QuestReadyEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.McaVillagerSnapshot;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.network.QuestCard;
import dev.otectus.mcaquests.network.QuestLogSyncS2CPacket;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.QuestReadyToastS2CPacket;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.situation.DynamicOfferSource;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.SituationOffer;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.quest.template.TemplateSpec;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Server-authoritative quest controller: builds the menu view, and handles accept / decline /
 * turn-in / abandon (spec sections 5, 18, 19, 26). Every client-driven entry point re-resolves and
 * re-validates against server state; the client is never trusted. Reads MCA state via
 * {@link McaCompat} only.
 */
public final class QuestManager {

    private QuestManager() {
    }

    // ---------------------------------------------------------------- packet entry points

    public static void openFromPacket(ServerPlayer player, UUID villagerUuid) {
        Entity villager = resolve(player, villagerUuid);
        if (villager != null) {
            sendMenu(player, villager);
        }
    }

    /** Used by the Phase 0 debug interaction. */
    public static void open(ServerPlayer player, Entity villager) {
        sendMenu(player, villager);
    }

    public static void acceptFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId, boolean accept) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        if (accept) {
            accept(player, villager, questId);
        }
        // decline is a no-op for now (it simply does not create state); both refresh the menu.
        sendMenu(player, villager);
        syncLog(player);
    }

    public static void turnInFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        turnIn(player, villager, questId);
        sendMenu(player, villager);
        syncLog(player);
    }

    public static void abandonFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        abandon(player, villager, questId);
        sendMenu(player, villager);
        syncLog(player);
    }

    @Nullable
    private static Entity resolve(ServerPlayer player, UUID villagerUuid) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(villagerUuid);
        return (entity != null && McaCompat.canPlayerInteract(player, entity)) ? entity : null;
    }

    // ---------------------------------------------------------------- menu construction

    public static void sendMenu(ServerPlayer player, Entity villager) {
        // Co-send community-project cards first so the client cache is populated before the quest menu
        // opens (drives the "View Project" button). Individual quests stay visually unchanged.
        ProjectManager.sendProjectMenu(player, villager);

        UUID villagerUuid = villager.getUUID();
        Component name = McaCompat.getVillagerDisplayName(villager);
        Component profession = McaCompat.getProfessionName(villager);
        int hearts = McaCompat.getHearts(player, villager);

        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, hearts, QuestMenuStatus.NO_QUESTS));
            return;
        }
        PlayerQuestData data = dataOpt.get();

        // 1) Active quests relevant here: given by this villager, or turn-in-able here per their mode.
        List<ActiveQuest> relevant = relevantActiveQuests(player, villager, data);
        if (!relevant.isEmpty()) {
            ActiveQuest active = relevant.get(0);
            Optional<QuestDefinition> defOpt = QuestDefinitions.resolve(active.questId());
            if (defOpt.isEmpty()) {
                // The definition disappeared on a datapack reload — fail gracefully (spec section 36).
                send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, hearts, QuestMenuStatus.BLOCKED));
                return;
            }
            QuestDefinition def = active.resolve(defOpt.get());
            boolean ready = isComplete(player, def, active) && canTurnInAt(active, def, villager);
            QuestMenuStatus status = ready ? QuestMenuStatus.READY : QuestMenuStatus.IN_PROGRESS;
            QuestCard card = buildCard(player, villager, def, active.textResolver(), active,
                    ready ? QuestDefinition.READY : QuestDefinition.IN_PROGRESS);
            send(player, QuestMenuDataS2CPacket.cards(villagerUuid, name, profession, hearts, status, List.of(card)));
            return;
        }

        // 2) Otherwise offer an eligible quest (if any and the player is under the active cap).
        if (data.activeCount() >= McaQuestsConfig.COMMON.maxActiveQuestsPerPlayer.get()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, hearts, QuestMenuStatus.NO_QUESTS));
            return;
        }
        List<QuestDefinition> eligible = eligibleOffers(player, villager, data);
        if (eligible.isEmpty()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, hearts, QuestMenuStatus.NO_QUESTS));
            return;
        }
        // Deterministic, server-authoritative selection: priority tiers (datapack-controlled; chain
        // continuations default above standalone) fill slots in turn, each tier weighted by the quest's
        // context-sensitive effective weight. Same player/villager/day yields the same offers.
        List<QuestDefinition> chosen = selectOffers(player, villager, data, eligible);
        List<QuestCard> cards = new ArrayList<>();
        for (QuestDefinition def : chosen) {
            buildOfferCard(player, villager, data, def).ifPresent(cards::add);
        }
        send(player, QuestMenuDataS2CPacket.cards(villagerUuid, name, profession, hearts, QuestMenuStatus.OFFER, cards));
    }

    /**
     * Builds the offer card for {@code def}, concretizing a template quest from a fresh resolution (the
     * values are deterministic per villager/day, so the accepted quest reproduces what was shown). A
     * template that cannot resolve right now (empty pool, unparsable substitution) is skipped.
     */
    private static Optional<QuestCard> buildOfferCard(ServerPlayer player, Entity villager, PlayerQuestData data,
                                                      QuestDefinition def) {
        if (!def.isTemplate()) {
            return Optional.of(buildCard(player, villager, def, null, null, QuestDefinition.OFFER));
        }
        TemplateSpec spec = def.template().get();
        QuestContext context = new QuestContext(player, villager, data, def.id());
        Optional<ResolvedTemplate> values = spec.resolveValues(context);
        Optional<TemplateSpec.Concrete> concrete = values.flatMap(spec::toConcrete);
        if (values.isEmpty() || concrete.isEmpty()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Skipping template offer '{}' — could not resolve its variables.", def.id());
            return Optional.empty();
        }
        QuestDefinition resolved = def.withConcrete(concrete.get());
        return Optional.of(buildCard(player, villager, resolved, new PlaceholderResolver(values.get()), null,
                QuestDefinition.OFFER));
    }

    private static QuestCard buildCard(ServerPlayer player, @Nullable Entity villager, QuestDefinition def,
                                       @Nullable PlaceholderResolver resolver, @Nullable ActiveQuest active,
                                       String dialogueState) {
        // Situation offers reuse the chain-label slot for a "village needs help" tag so the player can tell
        // an emergent, time-limited request from a standing offer (0.8.0).
        boolean situation = def.category().map(SituationOffer.CATEGORY::equals).orElse(false);
        Component label = situation ? Component.translatable("mcaquests.situation.card_tag") : chainLabel(def);
        // An add-on (e.g. MCA: Conversations) may voice this line in the villager's personality; falls back to
        // the quest's own static dialogue text when none is registered (QuestDialogueHooks).
        Component dialogue = QuestDialogueHooks.resolve(player, villager, def, dialogueState,
                def.dialogueOr(dialogueState, def.title(resolver), resolver));
        return new QuestCard(def.id(), def.title(resolver), label, dialogue,
                objectiveLines(player, def, active), rewardLines(def));
    }

    /** The relationship-arc context line for the UI (arc / "Part 2 of 4" / chapter), or empty for standalone quests. */
    private static Component chainLabel(QuestDefinition def) {
        return def.chain().flatMap(ChainSpec::label).orElse(Component.empty());
    }

    private static boolean isChainContinuation(QuestDefinition def) {
        return def.chain().map(chain -> chain.stage() > 1).orElse(false);
    }

    // ---------------------------------------------------------------- actions

    public static boolean accept(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<QuestDefinition> defOpt = QuestDefinitions.resolve(questId);
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (defOpt.isEmpty() || dataOpt.isEmpty()) {
            return false;
        }
        QuestDefinition def = defOpt.get();
        PlayerQuestData data = dataOpt.get();
        UUID villagerUuid = villager.getUUID();

        // Re-validate eligibility server-side; never trust the client's offered id.
        if (!eligibleOffers(player, villager, data).contains(def)) {
            return false;
        }
        if (data.activeCount() >= McaQuestsConfig.COMMON.maxActiveQuestsPerPlayer.get()
                || data.byVillager(villagerUuid).size() >= McaQuestsConfig.COMMON.maxActiveQuestsPerVillager.get()) {
            return false;
        }

        // For a template quest, freeze the resolved values now (deterministic — identical to the offer
        // shown today) so objectives/rewards never reroll for this accepted copy.
        ResolvedTemplate frozen = null;
        QuestDefinition accepted = def;
        PlaceholderResolver resolver = null;
        if (def.isTemplate()) {
            TemplateSpec spec = def.template().get();
            QuestContext context = new QuestContext(player, villager, data, def.id());
            Optional<ResolvedTemplate> values = spec.resolveValues(context);
            Optional<TemplateSpec.Concrete> concrete = values.flatMap(spec::toConcrete);
            if (values.isEmpty() || concrete.isEmpty()) {
                return false; // pool empty or substitution failed — cannot accept this template now
            }
            frozen = values.get();
            accepted = def.withConcrete(concrete.get());
            resolver = new PlaceholderResolver(frozen);
        }

        // A situation offer is anchored to its open instance: start time = the instance's open time so
        // the quest's deadline lands on the situation's master deadline, and the link lets completion /
        // expiry resolve the shared situation (0.8.0). A situation that closed between offer and accept
        // can no longer be accepted.
        long startTime = ((ServerLevel) player.level()).getGameTime();
        UUID situationLink = null;
        if (SituationIds.isSyntheticId(questId)) {
            MinecraftServer server = player.getServer();
            Optional<SituationInstance> instanceOpt = server == null
                    ? Optional.empty() : findOpenSituation(server, villager, questId);
            if (instanceOpt.isEmpty()) {
                return false;
            }
            startTime = instanceOpt.get().openGameTime();
            situationLink = instanceOpt.get().instanceId();
        }

        data.add(ActiveQuest.create(questId, villagerUuid,
                McaCompat.getVillagerDisplayName(villager),
                McaCompat.getProfessionId(villager).orElse(null),
                player.level().dimension().location(),
                startTime,
                accepted.objectives().size(), frozen, situationLink));
        if (situationLink != null && player.getServer() != null) {
            SituationSavedData.get(player.getServer()).recordParticipant(situationLink, player.getUUID());
        }
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, villager, accepted));
        McaCompat.setQuestGiverFollow(player, villager, McaQuestsConfig.COMMON.followGiverAfterAccept.get());
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            player.sendSystemMessage(QuestDialogueHooks.resolve(player, villager, accepted, QuestDefinition.ACCEPT,
                    Component.translatable("mcaquests.message.quest_accepted", accepted.title(resolver))));
        }
        return true;
    }

    public static boolean turnIn(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        Optional<QuestDefinition> defOpt = QuestDefinitions.resolve(questId);
        if (dataOpt.isEmpty() || defOpt.isEmpty()) {
            return false;
        }
        PlayerQuestData data = dataOpt.get();
        QuestDefinition base = defOpt.get();
        // Find a completable copy of this quest that may be turned in at THIS villager (mode-aware). Each
        // copy resolves its own template values, so completion is checked against its concrete objectives.
        Optional<ActiveQuest> activeOpt = data.active().stream()
                .filter(aq -> aq.questId().equals(questId) && !aq.rewardClaimed()
                        && canTurnInAt(aq, base, villager) && isComplete(player, aq.resolve(base), aq))
                .findFirst();
        return activeOpt.filter(active ->
                completeQuest(player, villager, active.resolve(base), active, data)).isPresent();
    }

    /** Auto-completion for {@link TurnInMode#SELF_COMPLETE} quests; called from the progress tick. */
    public static void selfComplete(ServerPlayer player, ActiveQuest active) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        Optional<QuestDefinition> defOpt = QuestDefinitions.resolve(active.questId());
        if (dataOpt.isEmpty() || defOpt.isEmpty() || active.rewardClaimed()) {
            return;
        }
        QuestDefinition def = active.resolve(defOpt.get());
        if (!isComplete(player, def, active)) {
            return;
        }
        completeQuest(player, resolveGiver(player, active), def, active, dataOpt.get());
        syncLog(player);
    }

    /**
     * Atomic, idempotent completion. Claims the reward slot first (blocks packet-spam dup), consumes
     * objective items, then grants rewards (hearts last), records cooldown/completion, and removes the
     * quest. {@code grantVillager} receives the hearts reward (may be null if the giver is gone).
     */
    private static boolean completeQuest(ServerPlayer player, Entity grantVillager,
                                         QuestDefinition def, ActiveQuest active, PlayerQuestData data) {
        if (active.rewardClaimed()) {
            return false;
        }
        active.setRewardClaimed(true);

        for (int i = 0; i < def.objectives().size(); i++) {
            def.objectives().get(i).consumeOnTurnIn(player, active.progress(i));
        }
        for (QuestReward reward : def.rewards()) {
            if (!(reward instanceof HeartsReward)) {
                reward.grant(player, grantVillager);
            }
        }
        for (QuestReward reward : def.rewards()) {
            if (reward instanceof HeartsReward) {
                reward.grant(player, grantVillager);
            }
        }
        grantVillageReputationRewards(player, grantVillager, def);

        long now = ((ServerLevel) player.level()).getGameTime();
        data.history().recordCompletion(def.id(), active.villagerUuid());
        switch (def.repeat().type()) {
            case COOLDOWN -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), now + def.cooldownTicks());
            case ONCE -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), Long.MAX_VALUE);
            case REPEATABLE -> { /* immediately available again */ }
        }
        releaseEscortMovement(player, def, active);
        data.remove(active);
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, grantVillager, def));
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            player.sendSystemMessage(QuestDialogueHooks.resolve(player, grantVillager, def, QuestDefinition.COMPLETE,
                    Component.translatable("mcaquests.message.quest_completed", def.title(active.textResolver()))));
        }
        // A situation offer resolves its shared situation on the first participant's completion (0.8.0).
        active.situationInstance().ifPresent(instanceId -> {
            if (player.getServer() != null) {
                SituationManager.resolveSuccess(player.getServer(), instanceId, player);
            }
        });
        return true;
    }

    /**
     * Applies any {@code village_reputation} rewards on a quest to the giver's village (independent
     * mod-side reputation), keyed identically to village-scoped projects so the {@code village_reputation}
     * condition reads the same value. No-op when the giver has no resolvable village.
     */
    private static void grantVillageReputationRewards(ServerPlayer player, @Nullable Entity grantVillager,
                                                      QuestDefinition def) {
        if (grantVillager == null || player.getServer() == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int amount = def.rewards().stream()
                .filter(r -> r instanceof dev.otectus.mcaquests.quest.reward.VillageReputationReward)
                .mapToInt(r -> ((dev.otectus.mcaquests.quest.reward.VillageReputationReward) r).amount())
                .sum();
        if (amount == 0) {
            return;
        }
        java.util.OptionalInt villageId = McaCompat.getHomeVillageId(grantVillager);
        if (villageId.isEmpty()) {
            villageId = McaCompat.findNearestVillageId(level, grantVillager.blockPosition(),
                    McaQuestsConfig.COMMON.defaultScopeFallbackRadius.get());
        }
        if (villageId.isPresent()) {
            dev.otectus.mcaquests.quest.reputation.ReputationService.award(
                    player.getServer(), "v:" + villageId.getAsInt(), amount, player);
        }
    }

    public static boolean abandon(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            return false;
        }
        Optional<ActiveQuest> active = dataOpt.get().find(questId, villager.getUUID());
        if (active.isEmpty()) {
            return false;
        }
        dataOpt.get().remove(active.get());
        QuestDefinitions.resolve(questId).ifPresent(def -> {
            releaseEscortMovement(player, active.get().resolve(def), active.get());
            dataOpt.get().history().recordOutcome(def.id(), active.get().villagerUuid(), QuestHistory.Outcome.ABANDONED);
            MinecraftForge.EVENT_BUS.post(new QuestAbandonedEvent(player, villager, def));
        });
        return true;
    }

    /**
     * Server-authoritative, idempotent quest failure — the single point every failure path routes
     * through (deadline / time-window / weather triggers and giver death). Records a FAILED outcome
     * (so {@code quest_failed} follow-ups can branch on it), applies the {@code failure} outcome
     * (heart penalty, then a {@code retry_after} cooldown or a permanent {@code block_retry} lock),
     * notifies the player with the quest's {@code failed} dialogue (falling back to the generic
     * message), posts {@link QuestFailedEvent}, and removes the quest. No rewards are granted, so a
     * failed quest never duplicates a completion. {@code giver} may be null (dead / unloaded); pass it
     * when known to skip a re-resolve.
     */
    public static void failQuest(ServerPlayer player, ActiveQuest active, QuestDefinition def,
                                 QuestFailedEvent.Reason reason, @Nullable Entity giver, PlayerQuestData data) {
        if (!data.active().contains(active)) {
            return; // already reached a terminal state this tick — never fail (or double-fail) twice
        }
        Entity resolvedGiver = giver != null ? giver : resolveGiver(player, active);
        long now = ((ServerLevel) player.level()).getGameTime();

        data.history().recordOutcome(def.id(), active.villagerUuid(), QuestHistory.Outcome.FAILED);
        def.failure().ifPresent(failure -> {
            if (failure.failureHearts() != 0 && resolvedGiver != null) {
                McaCompat.addHearts(player, resolvedGiver, failure.failureHearts());
            }
            if (failure.blockRetry()) {
                data.history().setCooldownUntil(def.id(), active.villagerUuid(), Long.MAX_VALUE);
            } else {
                failure.retryAfterTicks().ifPresent(retry ->
                        data.history().setCooldownUntil(def.id(), active.villagerUuid(), now + retry));
            }
        });
        releaseEscortMovement(player, def, active);
        data.remove(active);

        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, resolvedGiver, def, reason));
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            Component failLine = def.dialogueOr(QuestDefinition.FAILED,
                    Component.translatable("mcaquests.message.quest_failed", def.title(active.textResolver())),
                    active.textResolver());
            player.sendSystemMessage(QuestDialogueHooks.resolve(player, resolvedGiver, def, QuestDefinition.FAILED,
                    failLine));
        }
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Failed quest '{}' for {} (reason {}).",
                    def.id(), player.getGameProfile().getName(), reason);
        }
    }

    /**
     * Releases any escort movement the giver/escortee was under (lead {@code WALK_TARGET} or legacy
     * {@code FOLLOW}) when a quest reaches a terminal state, so a led villager doesn't keep walking after
     * the quest completes, is abandoned, or fails. Best-effort and fail-safe: only escort objectives, only
     * a currently-loaded target.
     */
    private static void releaseEscortMovement(ServerPlayer player, QuestDefinition def, ActiveQuest active) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < def.objectives().size(); i++) {
            if (def.objectives().get(i) instanceof EscortEntityObjective escort) {
                resolveEscortee(escort, active.progress(i), player, active, level).ifPresent(target -> {
                    McaCompat.releaseVillagerHold(target);   // un-freeze a still-waiting (Phase A) escortee
                    McaCompat.stopVillagerLeading(target);
                    McaCompat.setQuestGiverFollow(player, target, false);
                });
            }
        }
    }

    /** The escortee to clean up: the objective's locked target if one was pinned, else the villager target. */
    private static Optional<LivingEntity> resolveEscortee(EscortEntityObjective escort, ObjectiveProgress progress,
                                                          ServerPlayer player, ActiveQuest active, ServerLevel level) {
        UUID locked = progress.targetUuid();
        if (locked != null && level.getEntity(locked) instanceof LivingEntity le) {
            return Optional.of(le);
        }
        return escort.villager().resolve(player, active, level);
    }

    // ---------------------------------------------------------------- helpers

    public static boolean isComplete(ServerPlayer player, QuestDefinition def, ActiveQuest active) {
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).isSatisfied(player, active.progress(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code active} may be turned in at {@code villager}, honouring its turn-in mode (spec section 17). */
    public static boolean canTurnInAt(ActiveQuest active, QuestDefinition def, Entity villager) {
        if (!McaCompat.isMcaVillager(villager)) {
            return false;
        }
        boolean isGiver = villager.getUUID().equals(active.villagerUuid());
        return switch (def.turnIn().mode()) {
            case ORIGINAL_GIVER -> isGiver
                    || (McaQuestsConfig.COMMON.allowTurnInToSameProfessionIfOriginalMissing.get()
                        && sameProfessionAsGiver(active, villager));
            case ANY_VILLAGER -> true;
            case SAME_PROFESSION -> isGiver || sameProfessionAsGiver(active, villager);
            case SPECIFIED_PROFESSION -> ProfessionMatcher.matchesAny(def.turnIn().professions(),
                    McaCompat.getProfessionId(villager).orElse(null), profMode());
            case SELF_COMPLETE -> false; // completed automatically, never via the menu
        };
    }

    private static boolean sameProfessionAsGiver(ActiveQuest active, Entity villager) {
        ResourceLocation giverProfession = active.villagerProfession();
        ResourceLocation actual = McaCompat.getProfessionId(villager).orElse(null);
        return giverProfession != null && actual != null
                && ProfessionMatcher.matches(giverProfession, actual, profMode());
    }

    private static ProfessionMatchingMode profMode() {
        return McaQuestsConfig.COMMON.professionMatchingMode.get();
    }

    /** Active quests worth showing at this villager: ones it gave, or ones ready and turn-in-able here. */
    private static List<ActiveQuest> relevantActiveQuests(ServerPlayer player, Entity villager, PlayerQuestData data) {
        List<ActiveQuest> relevant = new ArrayList<>();
        for (ActiveQuest active : data.active()) {
            QuestDefinition base = QuestDefinitions.resolve(active.questId()).orElse(null);
            if (base == null) {
                continue;
            }
            QuestDefinition def = active.resolve(base);
            boolean isGiver = active.villagerUuid().equals(villager.getUUID());
            boolean turnInableHere = isComplete(player, def, active) && canTurnInAt(active, def, villager);
            if (isGiver || turnInableHere) {
                relevant.add(active);
            }
        }
        // Surface a ready, turn-in-able quest ahead of an in-progress one.
        relevant.sort(Comparator.comparingInt(active -> {
            QuestDefinition base = QuestDefinitions.resolve(active.questId()).orElse(null);
            QuestDefinition def = base == null ? null : active.resolve(base);
            boolean ready = def != null && isComplete(player, def, active) && canTurnInAt(active, def, villager);
            return ready ? 0 : 1;
        }));
        return relevant;
    }

    private static Entity resolveGiver(ServerPlayer player, ActiveQuest active) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, active.dimension()));
        return level != null ? level.getEntity(active.villagerUuid()) : null;
    }

    /** The open situation instance behind a synthetic offer id, scoped to the villager's village (0.8.0). */
    private static Optional<SituationInstance> findOpenSituation(MinecraftServer server, Entity villager,
                                                                ResourceLocation syntheticId) {
        Optional<ResourceLocation> sourceId = SituationIds.sourceIdOf(syntheticId);
        java.util.OptionalInt villageId = McaCompat.getHomeVillageId(villager);
        if (sourceId.isEmpty() || villageId.isEmpty()) {
            return Optional.empty();
        }
        return SituationSavedData.get(server).openInstancesInVillage(villageId.getAsInt()).stream()
                .filter(instance -> instance.defId().equals(sourceId.get()))
                .findFirst();
    }

    /**
     * Public read-only convenience overload for add-ons: the offers {@code villager} could currently give
     * {@code player}, resolving the player's quest data internally (empty when the player has none). Lets a
     * dialogue mod ask "does this villager have work for me?" without touching {@link PlayerQuestData}.
     */
    public static List<QuestDefinition> eligibleOffers(ServerPlayer player, Entity villager) {
        return QuestCapabilities.get(player)
                .map(data -> eligibleOffers(player, villager, data))
                .orElseGet(List::of);
    }

    /** True when {@code villager} has at least one eligible offer for {@code player} right now. */
    public static boolean hasEligibleOffer(ServerPlayer player, Entity villager) {
        return !eligibleOffers(player, villager).isEmpty();
    }

    /**
     * True when {@code player} has an active quest — given by, or turn-in-able at, {@code villager} — whose
     * objectives are all satisfied (ready to hand in now). Read-only.
     */
    public static boolean hasReadyTurnIn(ServerPlayer player, Entity villager) {
        return QuestCapabilities.get(player).map(data -> data.active().stream().anyMatch(active -> {
            QuestDefinition base = QuestDefinitions.resolve(active.questId()).orElse(null);
            if (base == null) {
                return false;
            }
            QuestDefinition def = active.resolve(base);
            return isComplete(player, def, active) && canTurnInAt(active, def, villager);
        })).orElse(false);
    }

    /**
     * Advances every {@link ExternalSignalObjective} in the player's active quests that matches
     * {@code signalId} (and, if the objective is villager-specific, {@code villagerUuid}) by one step, then
     * runs the same post-progress sequence as the per-tick handler (self-complete SELF_COMPLETE quests →
     * ready transitions → log sync) so a conversation-driven objective completes immediately. Generic: any
     * add-on that registers an {@code ExternalSignalObjective} type can push progress through it.
     * {@code villagerUuid} may be null when the signal is not tied to a specific villager.
     */
    public static void notifyExternalObjective(ServerPlayer player, ResourceLocation signalId,
                                               @Nullable UUID villagerUuid) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            return;
        }
        PlayerQuestData data = dataOpt.get();
        boolean advanced = false;
        for (ActiveQuest active : new ArrayList<>(data.active())) {
            QuestDefinition base = QuestDefinitions.resolve(active.questId()).orElse(null);
            if (base == null) {
                continue;
            }
            QuestDefinition def = active.resolve(base);
            List<QuestObjective> objectives = def.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective instanceof ExternalSignalObjective signal
                        && signal.matchesSignal(signalId, villagerUuid)) {
                    ObjectiveProgress progress = active.progress(i);
                    if (progress.count() < objective.required()) {
                        progress.add(1);
                        advanced = true;
                    }
                }
            }
        }
        if (!advanced) {
            return;
        }
        // Same order as QuestProgressEvents' per-tick sequence: auto-complete first, then ready + sync.
        for (ActiveQuest active : new ArrayList<>(data.active())) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                QuestDefinition def = active.resolve(base);
                if (def.turnIn().mode() == TurnInMode.SELF_COMPLETE && !active.rewardClaimed()
                        && isComplete(player, def, active)) {
                    selfComplete(player, active);
                }
            });
        }
        checkReadyTransitions(player);
        syncLog(player);
    }

    static List<QuestDefinition> eligibleOffers(ServerPlayer player, Entity villager, PlayerQuestData data) {
        long now = ((ServerLevel) player.level()).getGameTime();
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        boolean adult = McaCompat.isAdult(villager);
        int hearts = McaCompat.getHearts(player, villager);
        UUID villagerUuid = villager.getUUID();
        ProfessionMatchingMode mode = McaQuestsConfig.COMMON.professionMatchingMode.get();
        // One MCA snapshot for the whole pass: villager state is read once and reused by every
        // MCA-aware condition across all candidate quests (Phase 1 §3).
        McaVillagerSnapshot mcaSnapshot = new McaVillagerSnapshot(player, villager);
        List<QuestDefinition> filtered = QuestRegistry.all().stream()
                .filter(QuestDefinition::enabled)
                .filter(def -> def.giver().isGeneric()
                        || ProfessionMatcher.matchesAny(def.giver().professions(), profession, mode))
                .filter(def -> !def.giver().adultOnly() || adult)
                .filter(def -> def.giver().acceptsHearts(hearts))
                .filter(def -> !data.hasActive(def.id(), villagerUuid))
                .filter(def -> !data.history().onCooldown(def.id(), villagerUuid, now))
                .filter(def -> def.repeat().type() != RepeatRule.RepeatType.ONCE
                        || onceCompletionCount(data, def, villagerUuid) == 0)
                // effectiveConditions() folds chain prerequisites into the condition gate, so a later
                // stage can never be offered before its prerequisites are completed.
                .filter(def -> def.effectiveConditions()
                        .map(condition -> condition.test(
                                new QuestContext(player, villager, data, def.id(), mcaSnapshot)))
                        .orElse(true))
                .sorted(Comparator.comparing(def -> def.id().toString()))
                .toList();
        // Static quests first, then any open situations this villager can surface (0.8.0). Situation
        // offers compete in the same selection/shaping pipeline below.
        List<QuestDefinition> eligible = new ArrayList<>(collapseChainsToFurthestStage(filtered));
        eligible.addAll(DynamicOfferSource.collect(player, villager, data, profession, adult, hearts));
        return eligible;
    }

    /**
     * Within each chain, keep only the furthest unlocked stage so a villager never offers an earlier
     * and a later stage of the same arc at once (e.g. a still-on-cooldown stage 1 alongside stage 2).
     * Standalone quests and same-stage branches pass through unchanged. Deterministic.
     */
    private static List<QuestDefinition> collapseChainsToFurthestStage(List<QuestDefinition> defs) {
        Map<String, Integer> maxStage = new HashMap<>();
        for (QuestDefinition def : defs) {
            def.chain().ifPresent(chain -> maxStage.merge(chain.chain(), chain.stage(), Math::max));
        }
        return defs.stream()
                .filter(def -> def.chain()
                        .map(chain -> chain.stage() == maxStage.get(chain.chain()))
                        .orElse(true))
                .toList();
    }

    /**
     * Deterministic, server-authoritative offer selection from the eligible pool. Candidates are grouped
     * into priority tiers ({@link #effectivePriority}: a chain continuation defaults above standalone, and a
     * datapack can override with {@code priority}); the highest tiers fill slots first, and within a tier
     * each quest is weighted by its context-sensitive {@link QuestDefinition#effectiveWeight}. One
     * {@link McaVillagerSnapshot} is shared across the weight evaluations. Same player/villager/day → same
     * offers.
     */
    private static List<QuestDefinition> selectOffers(ServerPlayer player, Entity villager, PlayerQuestData data,
                                                      List<QuestDefinition> eligible) {
        long worldDay = ((ServerLevel) player.level()).getDayTime() / 24000L;
        int slots = McaQuestsConfig.COMMON.offersPerVillager.get();
        long seed = offerSeed(player, villager.getUUID(), worldDay);
        McaVillagerSnapshot snapshot = new McaVillagerSnapshot(player, villager);
        ToIntFunction<QuestDefinition> weightFn = def ->
                def.effectiveWeight(new QuestContext(player, villager, data, def.id(), snapshot));
        List<QuestDefinition> chosen = new ArrayList<>();
        List<Integer> tiers = eligible.stream().map(QuestManager::effectivePriority).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        for (int tier : tiers) {
            if (chosen.size() >= slots) {
                break;
            }
            List<QuestDefinition> bucket = eligible.stream().filter(def -> effectivePriority(def) == tier).toList();
            chosen.addAll(WeightedPicker.pickMany(bucket, weightFn, seed, slots - chosen.size()));
        }
        return chosen;
    }

    /**
     * Offer priority tier: explicit {@code priority}, else a situation offer defaults to the configured
     * {@code situationDefaultPriority} (so the village's needs stand out), else a chain continuation
     * (stage &gt; 1) defaults above standalone.
     */
    private static int effectivePriority(QuestDefinition def) {
        if (def.priority().isPresent()) {
            return def.priority().get();
        }
        if (def.category().map(SituationOffer.CATEGORY::equals).orElse(false)) {
            return McaQuestsConfig.COMMON.situationDefaultPriority.get();
        }
        return isChainContinuation(def) ? 1 : 0;
    }

    /** ONCE-quest completion count: per-villager for chain stages (1:1 arcs), global for standalone quests. */
    private static int onceCompletionCount(PlayerQuestData data, QuestDefinition def, UUID villagerUuid) {
        return def.chain().isPresent()
                ? data.history().completionCountByGiver(def.id(), villagerUuid)
                : data.history().completionCount(def.id());
    }

    private static List<Component> objectiveLines(ServerPlayer player, QuestDefinition def, @Nullable ActiveQuest active) {
        List<Component> lines = new ArrayList<>();
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            Component line = active != null
                    ? objective.describe(player, active, (ServerLevel) player.level())
                    : objective.describe();
            if (active != null) {
                int current = objective.current(player, active.progress(i));
                line = Component.empty().append(line)
                        .append(Component.literal("  (" + current + "/" + objective.required() + ")"));
            }
            lines.add(line);
        }
        return lines;
    }

    private static List<Component> rewardLines(QuestDefinition def) {
        return def.rewards().stream().map(QuestReward::describe).toList();
    }

    private static long offerSeed(ServerPlayer player, UUID villagerUuid, long worldDay) {
        return player.getUUID().hashCode() * 31L + villagerUuid.hashCode() * 17L + worldDay * 1000003L;
    }

    private static void send(ServerPlayer player, QuestMenuDataS2CPacket packet) {
        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Detects quests whose objectives just became complete and notifies the player once (toast +
     * {@link QuestReadyEvent}); resets the flag if a possession objective later drops below target.
     */
    public static void checkReadyTransitions(ServerPlayer player) {
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    QuestDefinition def = active.resolve(base);
                    boolean complete = isComplete(player, def, active);
                    if (complete && !active.readyNotified()) {
                        active.setReadyNotified(true);
                        MinecraftForge.EVENT_BUS.post(new QuestReadyEvent(player, def));
                        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new QuestReadyToastS2CPacket(def.title(active.textResolver())));
                    } else if (!complete && active.readyNotified()) {
                        active.setReadyNotified(false);
                    }
                });
            }
        });
    }

    /** Pushes the player's active-quest snapshot to the client for the quest log + HUD tracker. */
    public static void syncLog(ServerPlayer player) {
        QuestCapabilities.get(player).ifPresent(data -> {
            List<QuestLogEntry> entries = new ArrayList<>();
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    QuestDefinition def = active.resolve(base);
                    java.util.OptionalLong deadline = def.failure()
                            .map(failure -> failure.deadlineGameTime(active.startGameTime()))
                            .orElse(java.util.OptionalLong.empty());
                    entries.add(new QuestLogEntry(active.questId(), def.title(active.textResolver()), active.villagerName(),
                            chainLabel(def), objectiveLines(player, def, active), isComplete(player, def, active),
                            deadline));
                });
            }
            QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuestLogSyncS2CPacket(entries));
        });
    }

    // ---------------------------------------------------------------- diagnostics (/mcaquests debug)

    /**
     * Human-readable explanation of why {@code questId} is or is not offered by {@code villager} right now:
     * the per-quest gate checklist, per-villager chain progress, history counts, weight/priority, and a final
     * status. Drives {@code /mcaquests debug quest}. Read-only.
     */
    public static List<Component> explainOffer(ServerPlayer player, Entity villager, ResourceLocation questId) {
        List<Component> out = new ArrayList<>();
        QuestDefinition def = QuestDefinitions.resolve(questId).orElse(null);
        if (def == null) {
            out.add(Component.literal("Unknown quest '" + questId + "'."));
            return out;
        }
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            out.add(Component.literal("No quest data, or not on a server level."));
            return out;
        }
        PlayerQuestData data = dataOpt.get();
        UUID villagerUuid = villager.getUUID();
        long now = level.getGameTime();

        out.add(Component.literal("Quest " + questId + def.chain()
                .map(c -> " [chain '" + c.chain() + "' stage " + c.stage() + "]").orElse(" [standalone]")));

        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        out.add(line("enabled", def.enabled()));
        out.add(line("profession match", def.giver().isGeneric()
                || ProfessionMatcher.matchesAny(def.giver().professions(), profession, profMode())));
        out.add(line("adult ok", !def.giver().adultOnly() || McaCompat.isAdult(villager)));
        int hearts = McaCompat.getHearts(player, villager);
        out.add(line("hearts in range (" + hearts + ")", def.giver().acceptsHearts(hearts)));
        out.add(line("not already active here", !data.hasActive(questId, villagerUuid)));
        out.add(line("off cooldown", !data.history().onCooldown(questId, villagerUuid, now)));
        out.add(line("not a finished once-quest", def.repeat().type() != RepeatRule.RepeatType.ONCE
                || onceCompletionCount(data, def, villagerUuid) == 0));
        McaVillagerSnapshot snapshot = new McaVillagerSnapshot(player, villager);
        out.add(line("conditions + prerequisites", def.effectiveConditions()
                .map(c -> c.test(new QuestContext(player, villager, data, questId, snapshot))).orElse(true)));

        def.chain().ifPresent(c -> c.prerequisites().forEach(pre -> out.add(Component.literal(
                "    prereq " + pre + ": " + (data.history().completionCountByGiver(pre, villagerUuid) > 0
                        ? "completed with this villager" : "NOT completed with this villager")))));
        out.add(Component.literal("    history: completed " + data.history().completionCount(questId)
                + " (with this villager " + data.history().completionCountByGiver(questId, villagerUuid) + ")"
                + ", failed " + data.history().outcomeCount(questId, QuestHistory.Outcome.FAILED)
                + ", abandoned " + data.history().outcomeCount(questId, QuestHistory.Outcome.ABANDONED)));
        out.add(Component.literal("    priority " + effectivePriority(def) + ", effective weight "
                + def.effectiveWeight(new QuestContext(player, villager, data, questId, snapshot))
                + " (base " + def.weight() + ")"));

        List<QuestDefinition> eligible = eligibleOffers(player, villager, data);
        List<QuestDefinition> chosen = selectOffers(player, villager, data, eligible);
        out.add(status(offerStatus(player, villager, data, def, eligible, chosen)));
        return out;
    }

    /**
     * One status line per relationship-chain stage this {@code villager} could give (by profession), grouped
     * by chain and ordered by stage. Drives the chain section of {@code /mcaquests debug villager}. Read-only.
     */
    public static List<Component> explainChainAvailability(ServerPlayer player, Entity villager) {
        List<Component> out = new ArrayList<>();
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            out.add(Component.literal("No quest data for this player."));
            return out;
        }
        PlayerQuestData data = dataOpt.get();
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        List<QuestDefinition> chainQuests = QuestRegistry.all().stream()
                .filter(def -> def.chain().isPresent())
                .filter(def -> def.giver().isGeneric()
                        || ProfessionMatcher.matchesAny(def.giver().professions(), profession, profMode()))
                .sorted(Comparator.<QuestDefinition, String>comparing(def -> def.chain().get().chain())
                        .thenComparingInt(def -> def.chain().get().stage())
                        .thenComparing(def -> def.id().toString()))
                .toList();
        if (chainQuests.isEmpty()) {
            out.add(Component.literal("chains: (none for this villager's profession)"));
            return out;
        }
        List<QuestDefinition> eligible = eligibleOffers(player, villager, data);
        List<QuestDefinition> chosen = selectOffers(player, villager, data, eligible);
        out.add(Component.literal("chains:"));
        String current = null;
        for (QuestDefinition def : chainQuests) {
            String chain = def.chain().get().chain();
            if (!chain.equals(current)) {
                current = chain;
                out.add(Component.literal("  " + chain + ":"));
            }
            out.add(Component.literal("    stage " + def.chain().get().stage() + " " + def.id().getPath()
                    + " — " + offerStatus(player, villager, data, def, eligible, chosen)));
        }
        return out;
    }

    /** Compact offer status for one quest at one villager (shared by both debug commands). */
    private static String offerStatus(ServerPlayer player, Entity villager, PlayerQuestData data, QuestDefinition def,
                                      List<QuestDefinition> eligible, List<QuestDefinition> chosen) {
        UUID villagerUuid = villager.getUUID();
        long now = ((ServerLevel) player.level()).getGameTime();
        if (data.hasActive(def.id(), villagerUuid)) {
            return "ACTIVE (accepted from this villager)";
        }
        if (chosen.contains(def)) {
            return "OFFERED";
        }
        if (eligible.contains(def)) {
            return "ELIGIBLE (in the pool, not drawn this slot/day)";
        }
        if (!def.enabled()) {
            return "DISABLED";
        }
        if (def.repeat().type() == RepeatRule.RepeatType.ONCE && onceCompletionCount(data, def, villagerUuid) > 0) {
            return "COMPLETED (with this villager)";
        }
        if (data.history().onCooldown(def.id(), villagerUuid, now)) {
            return "ON_COOLDOWN";
        }
        if (passesIndividualFilters(player, villager, data, def)) {
            String by = def.chain().flatMap(c -> eligible.stream()
                    .filter(e -> e.chain()
                            .map(ec -> ec.chain().equals(c.chain()) && ec.stage() > c.stage()).orElse(false))
                    .map(e -> e.id().getPath()).findFirst()).orElse("a later stage");
            return "HIDDEN/SUPERSEDED (by " + by + ")";
        }
        return "LOCKED (prerequisite or condition unmet)";
    }

    /** Whether a quest passes every per-quest offer filter — everything {@link #eligibleOffers} checks bar the chain collapse. */
    private static boolean passesIndividualFilters(ServerPlayer player, Entity villager, PlayerQuestData data,
                                                   QuestDefinition def) {
        UUID villagerUuid = villager.getUUID();
        long now = ((ServerLevel) player.level()).getGameTime();
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        if (!def.enabled()
                || !(def.giver().isGeneric()
                        || ProfessionMatcher.matchesAny(def.giver().professions(), profession, profMode()))
                || (def.giver().adultOnly() && !McaCompat.isAdult(villager))
                || !def.giver().acceptsHearts(McaCompat.getHearts(player, villager))
                || data.hasActive(def.id(), villagerUuid)
                || data.history().onCooldown(def.id(), villagerUuid, now)
                || (def.repeat().type() == RepeatRule.RepeatType.ONCE
                        && onceCompletionCount(data, def, villagerUuid) != 0)) {
            return false;
        }
        McaVillagerSnapshot snapshot = new McaVillagerSnapshot(player, villager);
        return def.effectiveConditions()
                .map(c -> c.test(new QuestContext(player, villager, data, def.id(), snapshot))).orElse(true);
    }

    private static Component line(String label, boolean ok) {
        return Component.literal("  " + (ok ? "[ok] " : "[no] ") + label);
    }

    private static Component status(String text) {
        return Component.literal("=> " + text);
    }
}
