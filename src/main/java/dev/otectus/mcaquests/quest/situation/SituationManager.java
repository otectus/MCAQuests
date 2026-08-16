package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.api.event.SituationResolvedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.SituationToastS2CPacket;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.WeightedPicker;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.situation.SituationOutcomes.Outcome;
import dev.otectus.mcaquests.quest.situation.SituationThrottle.Decision;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.quest.situation.state.SituationStatus;
import dev.otectus.mcaquests.quest.situation.trigger.LowFoodTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.RaidTrigger;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.ProgressionStats;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Opens situations in response to detected {@link TriggerSignal}s (the "Living Village" phase, 0.8.0).
 * It matches a signal against the loaded {@link SituationDefinition}s, picks one deterministically (a
 * per-village/per-day weighted draw), and opens it through the throttle ({@link SituationThrottle}).
 * Opened situations are persisted in {@link SituationSavedData}; everything is server-authoritative.
 *
 * <p>{@link #tryOpen} is split out as a pure-ish core (takes the store + parameters, no server lookups)
 * so the throttle/dedupe/cooldown bookkeeping is unit-testable; later phases (offers, resolution) read
 * the instances this opens.
 */
public final class SituationManager {

    private static final long DAY_LENGTH = 24000L;
    /** Radius (blocks) around a village center within which players are toasted when a situation opens. */
    private static final double NOTIFY_RADIUS = 96.0D;

    private SituationManager() {
    }

    /** Dispatches a detected signal: matches definitions, picks one, and opens it if the throttle allows. */
    public static void onSignal(MinecraftServer server, TriggerSignal signal) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        List<SituationDefinition> matches = SituationRegistry.all().stream()
                .filter(SituationDefinition::enabled)
                .filter(def -> def.trigger().signalType() == signal.type())
                .filter(def -> def.trigger().matches(signal))
                .toList();
        if (matches.isEmpty()) {
            return;
        }

        SituationSavedData data = SituationSavedData.get(server);
        long now = server.overworld().getGameTime();
        int maxConcurrent = McaQuestsConfig.COMMON.maxConcurrentSituationsPerVillage.get();
        long globalCooldownTicks = McaQuestsConfig.COMMON.situationGlobalCooldownTicks.get();

        // Deterministic per-village/per-day weighted ordering; open the first that clears the throttle.
        long day = Math.floorDiv(now, DAY_LENGTH);
        long seed = ((long) signal.villageId() * 31L + day) * 7L + signal.type().ordinal();
        List<SituationDefinition> order = WeightedPicker.pickMany(matches, def -> def.offer().weight(), seed, matches.size());
        for (SituationDefinition def : order) {
            if (tryOpen(data, def, signal.villageId(), signal.villagerUuid(), signal.familyRootUuid(),
                    now, maxConcurrent, globalCooldownTicks).isPresent()) {
                notifyOpened(server, signal.villageId(), def);
                break;
            }
        }
    }

    /** Sends the "village needs help" toast to players near the village when a situation opens. */
    private static void notifyOpened(MinecraftServer server, int villageId, SituationDefinition def) {
        ServerLevel overworld = server.overworld();
        Optional<BlockPos> center = McaCompat.villageCenter(overworld, villageId);
        if (center.isEmpty()) {
            return;
        }
        Component fallback = Component.translatable("mcaquests.situation.generic");
        for (ServerPlayer player : overworld.players()) {
            if (player.blockPosition().closerThan(center.get(), NOTIFY_RADIUS)) {
                // Resolve per recipient so a {player} token in the situation title renders that player's MCA name.
                Component title = def.offer().titleOr(fallback, PlaceholderResolver.forPlayer(player));
                QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new SituationToastS2CPacket(title));
            }
        }
    }

    /** Convenience overload for a loaded definition; mints a random instance id and a per-instance seed. */
    public static Optional<SituationInstance> tryOpen(SituationSavedData data, SituationDefinition def, int villageId,
                                                      @Nullable UUID villager, @Nullable UUID familyRoot,
                                                      long now, int maxConcurrent, long globalCooldownTicks) {
        UUID instanceId = UUID.randomUUID();
        long seed = instanceId.getMostSignificantBits() ^ instanceId.getLeastSignificantBits();
        return tryOpen(data, def.id(), def.durationTicks(), def.cooldownTicks(), villageId, villager, familyRoot,
                now, maxConcurrent, globalCooldownTicks, instanceId, seed);
    }

    /**
     * The throttle + open core, in terms of primitives so it is unit-testable without bootstrapping the
     * registry-backed {@link SituationDefinition} codec. Returns the opened instance, or empty when the
     * open was suppressed (an instance of this definition is already open in the village, the per-village
     * concurrency cap is reached, or a per-definition / global cooldown is still active — each logged so
     * caps are never silent). On success it persists the instance and writes both cooldowns.
     */
    public static Optional<SituationInstance> tryOpen(SituationSavedData data, ResourceLocation defId,
                                                      int durationTicks, int cooldownTicks, int villageId,
                                                      @Nullable UUID villager, @Nullable UUID familyRoot,
                                                      long now, int maxConcurrent, long globalCooldownTicks,
                                                      UUID instanceId, long seed) {
        if (data.hasOpenOfDef(villageId, defId)) {
            McaQuests.LOGGER.debug("[MCA: Quests] Situation '{}' already open in village {}; not re-opening.",
                    defId, villageId);
            return Optional.empty();
        }
        Decision decision = SituationThrottle.evaluate(
                data.openCountInVillage(villageId), maxConcurrent,
                data.cooldownUntil(villageId, defId), data.globalCooldownUntil(villageId), now);
        if (!decision.allowed()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Situation '{}' suppressed in village {} ({}).",
                    defId, villageId, decision);
            return Optional.empty();
        }

        SituationInstance instance = new SituationInstance(instanceId, defId, villageId, villager, familyRoot,
                now, now + durationTicks, seed, SituationStatus.OPEN);
        data.putInstance(instance);
        data.setCooldownUntil(villageId, defId, now + cooldownTicks);
        data.setGlobalCooldownUntil(villageId, now + globalCooldownTicks);
        McaQuests.LOGGER.info("[MCA: Quests] Opened situation '{}' in village {} (closes in {} ticks).",
                defId, villageId, durationTicks);
        return Optional.of(instance);
    }

    /** All currently-open situation instances across every village (for commands/debug). */
    public static List<SituationInstance> openInstances(MinecraftServer server) {
        return SituationSavedData.get(server).allInstances().stream()
                .filter(SituationInstance::isOpen)
                .toList();
    }

    // ---------------------------------------------------------------- resolution

    /**
     * Resolves an open situation as a success — the first participant completed its offer quest. Applies
     * the {@code success} outcome (village reputation via {@code ReputationService}, plus hearts to the
     * focal villager) and closes it. Idempotent: a no-op once the situation is gone or already resolved.
     */
    public static void resolveSuccess(MinecraftServer server, UUID instanceId, @Nullable ServerPlayer player) {
        SituationSavedData data = SituationSavedData.get(server);
        data.getInstance(instanceId).filter(SituationInstance::isOpen).ifPresent(instance -> {
            SituationRegistry.get(instance.defId())
                    .ifPresent(def -> applyOutcome(server, instance, def.outcomes().success(), player));
            McaQuests.LOGGER.info("[MCA: Quests] Situation '{}' in village {} resolved: SUCCESS.",
                    instance.defId(), instance.villageId());
            data.removeInstance(instanceId);
            recordSituationSuccess(server, instance);
            postResolved(instance, SituationResolvedEvent.Resolution.SUCCESS, player);
        });
    }

    /**
     * ProgressionStats (spec section 11.2): +1 per online participant, keyed by the situation's SOURCE
     * definition id. Success only — failure/cleared resolutions do not count. Inline at this funnel
     * (not an event listener) so it lands the same tick as the resolution itself.
     */
    private static void recordSituationSuccess(MinecraftServer server, SituationInstance instance) {
        for (UUID uuid : instance.participants()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null) {
                QuestCapabilities.get(participant).ifPresent(pdata ->
                        ProgressionStats.increment(pdata.stats().situationSuccesses(), instance.defId(), 1));
            }
        }
    }

    /**
     * Resolves an open situation as a failure — its deadline expired or the condition turned against the
     * village. Applies the {@code failure} outcome (a reputation penalty), fails any still-active offer
     * copies, and closes it.
     */
    public static void resolveFailure(MinecraftServer server, UUID instanceId) {
        SituationSavedData data = SituationSavedData.get(server);
        data.getInstance(instanceId).filter(SituationInstance::isOpen).ifPresent(instance -> {
            SituationRegistry.get(instance.defId())
                    .ifPresent(def -> applyOutcome(server, instance, def.outcomes().failure(), null));
            failOutstandingCopies(server, instanceId);
            McaQuests.LOGGER.info("[MCA: Quests] Situation '{}' in village {} resolved: FAILURE.",
                    instance.defId(), instance.villageId());
            data.removeInstance(instanceId);
            postResolved(instance, SituationResolvedEvent.Resolution.FAILURE, null);
        });
    }

    /**
     * Resolves an open situation as cleared — the condition lifted on its own (e.g. the raid ended, food
     * recovered) before anyone acted. Applies the usually-neutral {@code cleared} outcome and closes it,
     * leaving any active offer copies to expire on their own deadline.
     */
    public static void resolveCleared(MinecraftServer server, UUID instanceId) {
        SituationSavedData data = SituationSavedData.get(server);
        data.getInstance(instanceId).filter(SituationInstance::isOpen).ifPresent(instance -> {
            SituationRegistry.get(instance.defId())
                    .ifPresent(def -> applyOutcome(server, instance, def.outcomes().cleared(), null));
            McaQuests.LOGGER.info("[MCA: Quests] Situation '{}' in village {} resolved: CLEARED.",
                    instance.defId(), instance.villageId());
            data.removeInstance(instanceId);
            postResolved(instance, SituationResolvedEvent.Resolution.CLEARED, null);
        });
    }

    /**
     * The single funnel through which every resolution posts {@link SituationResolvedEvent} (Risk R1):
     * {@code resolveSuccess}/{@code resolveFailure}/{@code resolveCleared} each call this exactly once,
     * after their outcome has applied and the instance has been removed, so no path double-posts.
     */
    private static void postResolved(SituationInstance instance, SituationResolvedEvent.Resolution resolution,
                                     @Nullable ServerPlayer resolvingPlayer) {
        MinecraftForge.EVENT_BUS.post(new SituationResolvedEvent(instance.defId(), instance.villageId(), resolution,
                Set.copyOf(instance.participants()), resolvingPlayer));
    }

    /** Periodic maintenance: expire situations past their deadline and clear ones whose condition lifted. */
    public static void tick(MinecraftServer server) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (SituationInstance instance : SituationSavedData.get(server).allInstances()) {
            if (!instance.isOpen()) {
                continue;
            }
            boolean expired = instance.isExpiredAt(now);
            boolean cleared = !expired && isCleared(server, instance);
            switch (tickDecision(true, expired, cleared)) {
                case EXPIRE -> resolveFailure(server, instance.instanceId());
                case CLEAR -> resolveCleared(server, instance.instanceId());
                case NONE -> { /* still running */ }
            }
        }
    }

    /** Pure tick state machine, extracted for unit testing. */
    public static TickAction tickDecision(boolean open, boolean expired, boolean cleared) {
        if (!open) {
            return TickAction.NONE;
        }
        if (expired) {
            return TickAction.EXPIRE;
        }
        return cleared ? TickAction.CLEAR : TickAction.NONE;
    }

    /** Whether an open situation's underlying condition has lifted (currently raid-ended / food-recovered). */
    private static boolean isCleared(MinecraftServer server, SituationInstance instance) {
        Optional<SituationDefinition> defOpt = SituationRegistry.get(instance.defId());
        if (defOpt.isEmpty()) {
            return false;
        }
        ServerLevel overworld = server.overworld();
        int villageId = instance.villageId();
        SituationTrigger trigger = defOpt.get().trigger();
        if (trigger instanceof RaidTrigger) {
            return McaCompat.villageCenter(overworld, villageId)
                    .map(center -> !McaCompat.isRaidActive(overworld, center))
                    .orElse(false);
        }
        if (trigger instanceof LowFoodTrigger lowFood) {
            OptionalInt food = McaCompat.getVillageFoodCount(overworld, villageId);
            return food.isPresent() && food.getAsInt() > lowFood.threshold();
        }
        return false;
    }

    private static void applyOutcome(MinecraftServer server, SituationInstance instance, Outcome outcome,
                                     @Nullable ServerPlayer player) {
        // §29.5: a situation's standing goes to the player who resolved it, not to everyone nearby
        // and not to an anonymous village total. With nobody to credit there is nothing to award.
        if (outcome.reputation() != 0 && player != null) {
            var community = dev.otectus.mcaquests.quest.reputation.QuestReputation
                    .inLevel(server.overworld(), instance.villageId());
            dev.otectus.mcaquests.quest.reputation.QuestReputation.award(
                    dev.otectus.mcaquests.compat.ReputationAward
                            .builder(server, player.getUUID(), community.dimension(),
                                    community.villageId(),
                                    dev.otectus.mcaquests.quest.reputation.QuestReputation.SOURCE)
                            .delta(outcome.reputation())
                            .incident(dev.otectus.mcaquests.quest.reputation.QuestReputationBlock
                                    .Incidents.SITUATION_RESOLVED)
                            .dedupeKey(dev.otectus.mcaquests.quest.reputation.ReputationDedupe
                                    .situation(instance.instanceId(), player.getUUID(),
                                            outcome.reputation() >= 0 ? "success" : "failure"))
                            .context("source_title", instance.defId().getPath())
                            .build());
        }
        if (outcome.hearts() != 0) {
            instance.villagerUuid().ifPresent(uuid ->
                    McaCompat.pushVillageHearts(server.overworld(), instance.villageId(), uuid, outcome.hearts()));
        }
    }

    private static void failOutstandingCopies(MinecraftServer server, UUID instanceId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestCapabilities.get(player).ifPresent(data -> {
                List<ActiveQuest> toFail = new ArrayList<>();
                for (ActiveQuest active : data.active()) {
                    if (active.situationInstance().map(instanceId::equals).orElse(false)) {
                        toFail.add(active);
                    }
                }
                for (ActiveQuest active : toFail) {
                    QuestDefinitions.resolve(active.questId()).ifPresent(base ->
                            QuestManager.failQuest(player, active, active.resolve(base),
                                    QuestFailedEvent.Reason.SITUATION_CLOSED, null, data));
                }
            });
        }
    }

    /** Outcome of a maintenance tick for one open situation. */
    public enum TickAction {
        NONE,
        EXPIRE,
        CLEAR
    }
}
