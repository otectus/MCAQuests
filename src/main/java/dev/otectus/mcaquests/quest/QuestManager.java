package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
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
import dev.otectus.mcaquests.quest.objective.QuestObjective;
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
            Optional<QuestDefinition> defOpt = QuestRegistry.get(active.questId());
            if (defOpt.isEmpty()) {
                // The definition disappeared on a datapack reload — fail gracefully (spec section 36).
                send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, hearts, QuestMenuStatus.BLOCKED));
                return;
            }
            QuestDefinition def = active.resolve(defOpt.get());
            boolean ready = isComplete(player, def, active) && canTurnInAt(active, def, villager);
            QuestMenuStatus status = ready ? QuestMenuStatus.READY : QuestMenuStatus.IN_PROGRESS;
            QuestCard card = buildCard(player, def, active.textResolver(), active,
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
        long worldDay = ((ServerLevel) player.level()).getDayTime() / 24000L;
        int slots = McaQuestsConfig.COMMON.offersPerVillager.get();
        long seed = offerSeed(player, villagerUuid, worldDay);
        // Prioritize relationship-arc continuations (stage > 1): fill slots from those first, then the
        // rest. Deterministic — same player/villager/day yields the same offers.
        List<QuestDefinition> continuations = eligible.stream().filter(QuestManager::isChainContinuation).toList();
        List<QuestDefinition> standalone = eligible.stream().filter(def -> !isChainContinuation(def)).toList();
        List<QuestDefinition> chosen = new ArrayList<>(
                WeightedPicker.pickMany(continuations, QuestDefinition::weight, seed, slots));
        if (chosen.size() < slots) {
            chosen.addAll(WeightedPicker.pickMany(standalone, QuestDefinition::weight, seed, slots - chosen.size()));
        }
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
            return Optional.of(buildCard(player, def, null, null, QuestDefinition.OFFER));
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
        return Optional.of(buildCard(player, resolved, new PlaceholderResolver(values.get()), null, QuestDefinition.OFFER));
    }

    private static QuestCard buildCard(ServerPlayer player, QuestDefinition def, @Nullable PlaceholderResolver resolver,
                                       @Nullable ActiveQuest active, String dialogueState) {
        return new QuestCard(def.id(), def.title(resolver), chainLabel(def),
                def.dialogueOr(dialogueState, def.title(resolver), resolver),
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
        Optional<QuestDefinition> defOpt = QuestRegistry.get(questId);
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

        data.add(ActiveQuest.create(questId, villagerUuid,
                McaCompat.getVillagerDisplayName(villager),
                McaCompat.getProfessionId(villager).orElse(null),
                player.level().dimension().location(),
                ((ServerLevel) player.level()).getGameTime(),
                accepted.objectives().size(), frozen));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, villager, accepted));
        McaCompat.setQuestGiverFollow(player, villager, McaQuestsConfig.COMMON.followGiverAfterAccept.get());
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            player.sendSystemMessage(Component.translatable("mcaquests.message.quest_accepted", accepted.title(resolver)));
        }
        return true;
    }

    public static boolean turnIn(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        Optional<QuestDefinition> defOpt = QuestRegistry.get(questId);
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
        Optional<QuestDefinition> defOpt = QuestRegistry.get(active.questId());
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
        data.history().recordCompletion(def.id());
        switch (def.repeat().type()) {
            case COOLDOWN -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), now + def.cooldownTicks());
            case ONCE -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), Long.MAX_VALUE);
            case REPEATABLE -> { /* immediately available again */ }
        }
        data.remove(active);
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, grantVillager, def));
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            player.sendSystemMessage(Component.translatable("mcaquests.message.quest_completed",
                    def.title(active.textResolver())));
        }
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
            ProjectSavedData.get(player.getServer()).addReputation("v:" + villageId.getAsInt(), amount);
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
        QuestRegistry.get(questId).ifPresent(def -> {
            dataOpt.get().history().recordOutcome(def.id(), QuestHistory.Outcome.ABANDONED);
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

        data.history().recordOutcome(def.id(), QuestHistory.Outcome.FAILED);
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
        data.remove(active);

        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, resolvedGiver, def, reason));
        if (McaQuestsConfig.COMMON.questChatMessages.get()) {
            player.sendSystemMessage(def.dialogueOr(QuestDefinition.FAILED,
                    Component.translatable("mcaquests.message.quest_failed", def.title(active.textResolver())),
                    active.textResolver()));
        }
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Failed quest '{}' for {} (reason {}).",
                    def.id(), player.getGameProfile().getName(), reason);
        }
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
            QuestDefinition base = QuestRegistry.get(active.questId()).orElse(null);
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
            QuestDefinition base = QuestRegistry.get(active.questId()).orElse(null);
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
                        || data.history().completionCount(def.id()) == 0)
                // effectiveConditions() folds chain prerequisites into the condition gate, so a later
                // stage can never be offered before its prerequisites are completed.
                .filter(def -> def.effectiveConditions()
                        .map(condition -> condition.test(
                                new QuestContext(player, villager, data, def.id(), mcaSnapshot)))
                        .orElse(true))
                .sorted(Comparator.comparing(def -> def.id().toString()))
                .toList();
        return collapseChainsToFurthestStage(filtered);
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

    private static List<Component> objectiveLines(ServerPlayer player, QuestDefinition def, @Nullable ActiveQuest active) {
        List<Component> lines = new ArrayList<>();
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            Component line = objective.describe();
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
                QuestRegistry.get(active.questId()).ifPresent(base -> {
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
                QuestRegistry.get(active.questId()).ifPresent(base -> {
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
}
