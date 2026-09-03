package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadObjective;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.FtbqEditorIdsSync;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.guidance.GuidanceService;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.objective.BreakBlockObjective;
import dev.otectus.mcaquests.quest.objective.BreedAnimalsObjective;
import dev.otectus.mcaquests.quest.objective.BuildNearLocationObjective;
import dev.otectus.mcaquests.quest.objective.CraftItemObjective;
import dev.otectus.mcaquests.quest.objective.CureVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DefendLocationObjective;
import dev.otectus.mcaquests.quest.objective.DefendVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.EnterStructureObjective;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.FishItemObjective;
import dev.otectus.mcaquests.quest.objective.HealEntityObjective;
import dev.otectus.mcaquests.quest.objective.KillEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.PlaceBlockObjective;
import dev.otectus.mcaquests.quest.objective.ProtectEntityObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.ReachLocationObjective;
import dev.otectus.mcaquests.quest.objective.SleepOrRestObjective;
import dev.otectus.mcaquests.quest.objective.TalkToProfessionObjective;
import dev.otectus.mcaquests.quest.objective.TameAnimalObjective;
import dev.otectus.mcaquests.quest.objective.TradeWithVillagerObjective;
import dev.otectus.mcaquests.quest.objective.VillagerTargeted;
import dev.otectus.mcaquests.quest.objective.VisitBiomeObjective;
import dev.otectus.mcaquests.quest.objective.VisitDimensionObjective;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.quest.situation.SituationDetectors;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.DeadGiversData;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Server-side progress tracking for event-driven objectives (spec section 19). Only credits the
 * player who earned it; runs only for players who actually have an active quest of the matching type.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestProgressEvents {

    private QuestProgressEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Before the log is synced, so a quest whose giver died while this player was offline is
            // already gone from it rather than appearing for a moment and then vanishing.
            reconcileDeadGivers(player);
            QuestManager.syncLog(player);
            // Task M5.1: FTB editor known-ids sync (no-op unless FTB Quests is loaded + syncFtbqEditorIds).
            FtbqEditorIdsSync.maybeSend(player);
        }
    }

    /**
     * Applies {@link #onGiverDeath}'s rule to the deaths this player was offline for.
     *
     * <p>Only givers {@link DeadGiversData} recorded as <em>dead</em> count. A giver who is merely
     * unloaded — the usual state of a village nobody is standing in — is left exactly alone, because
     * "not in memory" and "no longer exists" are the same empty from {@code ServerLevel#getEntity} and
     * failing on it would take quests away for a chunk boundary.
     */
    private static void reconcileDeadGivers(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DeadGiversData dead = DeadGiversData.get(server);
        if (dead.isEmpty()) {
            return;
        }
        boolean globalFail = McaQuestsConfig.COMMON.failQuestIfGiverDies.get();
        QuestCapabilities.get(player).ifPresent(data -> {
            List<ActiveQuest> failed = new ArrayList<>();
            for (ActiveQuest active : data.active()) {
                if (!dead.isDead(active.villagerUuid())) {
                    continue;
                }
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    QuestDefinition def = active.resolve(base);
                    if (def.turnIn().mode().failsOnGiverDeath(def.failure(), globalFail)) {
                        failed.add(active);
                    }
                });
            }
            for (ActiveQuest active : failed) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base ->
                        QuestManager.failQuest(player, active, active.resolve(base),
                                QuestFailedEvent.Reason.GIVER_DIED, null, data));
            }
            if (!failed.isEmpty()) {
                player.sendSystemMessage(Component.translatable("mcaquests.message.giver_died"));
            }
        });
    }

    /**
     * The player a kill belongs to, whether they struck the blow or merely arranged it.
     *
     * <p>Only {@code getSource().getEntity()} used to count, which is melee and player-fired projectiles
     * and nothing else: a wolf's kill, a TNT kill, a lava or fall-damage kill after the player landed the
     * first hit all read as nobody's, while the quest text says "kill". So this falls back to the
     * tamed animal's owner, and then to the vanilla notion of credit
     * ({@link LivingEntity#getKillCredit()} — the last player to hurt them, within the same window the
     * death message uses).
     *
     * <p>A player who dies is never credited with their own death, and credit never crosses levels — the
     * objectives all reason about one {@link ServerLevel}.
     */
    static Optional<ServerPlayer> creditedPlayer(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        ServerPlayer credited = null;
        if (event.getSource().getEntity() instanceof ServerPlayer direct) {
            credited = direct;
        } else if (event.getSource().getEntity() instanceof OwnableEntity pet
                && pet.getOwner() instanceof ServerPlayer owner) {
            credited = owner;
        } else if (dead.getKillCredit() instanceof ServerPlayer lastHurtBy) {
            credited = lastHurtBy;
        }
        if (credited == null || credited == dead || credited.level() != dead.level()) {
            return Optional.empty();
        }
        return Optional.of(credited);
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // Credit the responsible player: the one who struck, whose pet struck, or whose hit vanilla
        // still remembers when something else finished the job.
        Optional<ServerPlayer> credited = creditedPlayer(event);
        if (credited.isPresent()) {
            ServerPlayer player = credited.get();
            LivingEntity dead = event.getEntity();
            ServerLevel level = (ServerLevel) player.level();
            forActiveObjectives(player, KillEntityObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(dead)) {
                            progress.add(1);
                        }
                    });
            forActiveObjectives(player, DefendVillagerObjective.class,
                    (objective, active, progress) -> objective.onKill(player, active, progress, dead, level));
            forActiveObjectives(player, DefendLocationObjective.class,
                    (objective, active, progress) -> objective.onKill(player, active, progress, dead, level));
        }
    }

    /**
     * When any entity dies, fail (or reset) {@code protect_entity} objectives whose target it was.
     * Runs for every online player so a quest fails even if the protected villager died to something
     * other than the player. Collects-then-acts so completion never mutates {@code active()} mid-loop.
     */
    @SubscribeEvent
    public static void onProtectedDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // Protect targets are always MCA villagers; skip the common case (any mob dying) cheaply.
        if (dead.level().isClientSide() || !McaCompat.isMcaVillager(dead)) {
            return;
        }
        MinecraftServer server = dead.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = (ServerLevel) dead.level();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestCapabilities.get(player).ifPresent(data -> failProtectQuestsFor(player, data, dead, level));
        }
    }

    private static void failProtectQuestsFor(ServerPlayer player, PlayerQuestData data,
                                             LivingEntity dead, ServerLevel level) {
        List<ActiveQuest> toFail = new ArrayList<>();
        for (ActiveQuest active : data.active()) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                List<QuestObjective> objectives = active.resolve(base).objectives();
                for (int i = 0; i < objectives.size(); i++) {
                    if (objectives.get(i) instanceof ProtectEntityObjective protect) {
                        ObjectiveProgress progress = active.progress(i);
                        if (progress.elapsedTicks() < protect.durationTicks()
                                && protect.matchesTarget(dead, player, active, progress, level)) {
                            if (protect.failOnDeath()) {
                                toFail.add(active);
                            } else {
                                progress.resetElapsed();
                            }
                        }
                    }
                }
            });
        }
        for (ActiveQuest active : toFail) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base ->
                    QuestManager.failQuest(player, active, active.resolve(base),
                            QuestFailedEvent.Reason.PROTECT_TARGET_DIED, dead, data));
        }
        if (!toFail.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mcaquests.message.protect_target_died"));
        }
    }

    /**
     * When an MCA villager dies, fail any <em>engaged</em> staged escort whose locked escortee it was — i.e.
     * the player had already reached the escortee and the escort had truly begun. A still-waiting (Phase A)
     * escortee is held invulnerable and cannot reach here; the {@code engaged} guard also makes a forced
     * ({@code /kill}) Phase-A death a no-op, per the design.
     */
    @SubscribeEvent
    public static void onEscortTargetDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !McaCompat.isMcaVillager(dead)) {
            return;
        }
        MinecraftServer server = dead.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestCapabilities.get(player).ifPresent(data -> failEscortQuestsFor(player, data, dead));
        }
    }

    private static void failEscortQuestsFor(ServerPlayer player, PlayerQuestData data, LivingEntity dead) {
        UUID deadId = dead.getUUID();
        List<ActiveQuest> toFail = new ArrayList<>();
        for (ActiveQuest active : data.active()) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                List<QuestObjective> objectives = active.resolve(base).objectives();
                for (int i = 0; i < objectives.size(); i++) {
                    if (objectives.get(i) instanceof EscortEntityObjective escort && escort.isStaged()) {
                        ObjectiveProgress progress = active.progress(i);
                        if (progress.count() < 1 && progress.extra().getBoolean("engaged")
                                && deadId.equals(progress.targetUuid())) {
                            toFail.add(active);
                        }
                    }
                }
            });
        }
        // Pass null (not the dead escortee) so the heart penalty lands on the giver via resolveGiver.
        for (ActiveQuest active : toFail) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base ->
                    QuestManager.failQuest(player, active, active.resolve(base),
                            QuestFailedEvent.Reason.ESCORT_TARGET_DIED, null, data));
        }
        if (!toFail.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mcaquests.message.escort_target_died"));
        }
    }

    /**
     * Works out the one thing the player is being sent to, and outlines the villager it is about.
     *
     * <p>Both halves come from one walk of one quest — the tracked one — so the beam and the outline
     * can never end up on different quests. See {@code GuidanceService} for how that quest and that
     * objective are chosen.
     *
     * <p>This used to outline a villager for <b>every</b> incomplete villager-targeted objective of
     * <b>every</b> active quest, and, for a quest that named no villager at all, to fall back to
     * outlining the giver for the quest's entire lifetime. A player holding five errands therefore had
     * five permanently glowing villagers, four of which were glowing only because they had once handed
     * out a quest. Highlighting meant "I have spoken to this person", which is not worth a colour.
     *
     * <p>Now a villager glows when reaching them is the thing the player is actually doing: the
     * escortee they are walking, the recipient of the delivery they are carrying, the person to heal
     * or cure or find — and the giver, once the quest is finished and handing it back is the last step.
     * {@code highlightAllActiveQuests} restores the old behaviour for anyone who preferred it.
     *
     * <p>By default the outline is drawn <b>on the quest owner's client only</b> — see
     * {@link HighlightService}. The older server-side vanilla Glowing effect is still available behind
     * {@code highlightUsesGlowingEffect} for packs whose shaders or minimaps key off the real effect; it
     * is visible to everyone on the server, which is why it is no longer the default.
     */
    private static void highlightTargets(ServerPlayer player, ServerLevel level) {
        Optional<PlayerQuestData> capability = QuestCapabilities.get(player);
        if (capability.isEmpty()) {
            GuidanceService.clear(player);
            HighlightService.clear(player);
            return;
        }
        PlayerQuestData data = capability.get();
        Optional<LivingEntity> focused = GuidanceService.update(player, level, data);
        if (!McaQuestsConfig.COMMON.highlightQuestTargets.get()) {
            HighlightService.clear(player);
            return;
        }
        List<LivingEntity> targets = McaQuestsConfig.COMMON.highlightAllActiveQuests.get()
                ? everyActiveTarget(player, level, data)
                : focused.map(List::of).orElseGet(List::of);
        if (McaQuestsConfig.COMMON.highlightUsesGlowingEffect.get()) {
            targets.forEach(QuestProgressEvents::glow);
            HighlightService.clear(player); // don't double-outline when the effect already does it
            return;
        }
        int[] ids = new int[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            ids[i] = targets.get(i).getId();
        }
        HighlightService.send(player, ids);
    }

    /**
     * Every villager any active quest names, which is what the mod did before 1.5.0.
     *
     * <p>Kept behind {@code highlightAllActiveQuests} rather than deleted: a player who runs one quest
     * at a time never saw the problem this replaced, and for them the old behaviour is simply "my quest
     * target glows". The giver fallback is deliberately <em>not</em> reproduced — outlining somebody
     * because they once gave you a quest was the part that carried no information.
     */
    private static List<LivingEntity> everyActiveTarget(ServerPlayer player, ServerLevel level,
                                                        PlayerQuestData data) {
        List<LivingEntity> targets = new ArrayList<>();
        for (ActiveQuest active : data.active()) {
            QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                List<QuestObjective> objectives = active.resolve(base).objectives();
                for (int i = 0; i < objectives.size(); i++) {
                    if (objectives.get(i) instanceof VillagerTargeted targeted) {
                        targeted.highlightTarget(player, active, active.progress(i), level)
                                .ifPresent(targets::add);
                    }
                }
            });
        }
        return targets;
    }

    /** Applies a brief, particle-free Glowing so the target shows through walls; refreshed by the poll. */
    private static void glow(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
    }

    /**
     * Drops the player's tracked highlight set so the next poll resends from scratch. Needed on logout
     * (otherwise the map grows one entry per player forever) and on a dimension change, because entity
     * network ids are per-dimension and get reused — a remembered id from the overworld could otherwise
     * suppress a legitimate send in the Nether.
     */
    @SubscribeEvent
    public static void onLoggedOutClearHighlights(PlayerEvent.PlayerLoggedOutEvent event) {
        HighlightService.forget(event.getEntity().getUUID());
        GuidanceService.forget(event.getEntity().getUUID());
        // The two throttle stamps below are per player and were never cleared, so a long-running server
        // kept one entry per player who had ever logged in.
        lastTownsteadPoll.remove(event.getEntity().getUUID());
        lastBankedRetryDay.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimensionClearHighlights(PlayerEvent.PlayerChangedDimensionEvent event) {
        HighlightService.forget(event.getEntity().getUUID());
        GuidanceService.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            // Forgetting alone only stops the next send being suppressed; something still has to make
            // one happen, and a player who has just walked through a portal should not spend a second
            // in the Nether with the overworld's waypoints on their map.
            GuidanceService.markDirty(player);
        }
    }

    /**
     * Resends guidance after a death.
     *
     * <p>Both halves are needed. The respawned player's destinations are usually identical to the ones
     * they had when they died, so the equality suppression would drop the first snapshot after the
     * respawn — and the client cleared everything it had when the level was replaced. Forgetting makes
     * the send possible; marking makes it happen this tick rather than at the next poll.
     */
    @SubscribeEvent
    public static void onRespawnResendGuidance(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GuidanceService.forget(player.getUUID());
            GuidanceService.markDirty(player);
        }
    }

    /**
     * Recomputes guidance for the players a mutation marked, once, at the end of the tick.
     *
     * <p>The counterpart of {@code QuestManager.syncLog} marking them: a turn-in that completes a chain
     * and starts its next quest marks the same player several times in one tick and is answered here
     * once. Everything else about a guidance recompute — the search budget, the equality suppression —
     * is unchanged, because this runs exactly the pass the once-a-second poll runs.
     */
    @SubscribeEvent
    public static void onServerTickFlushGuidance(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Set<UUID> dirty = GuidanceService.drainDirty();
        if (dirty.isEmpty()) {
            return;
        }
        for (UUID id : dirty) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            // A player who logged out between the mutation and the flush has nothing to be told.
            if (player != null && player.level() instanceof ServerLevel level) {
                highlightTargets(player, level);
            }
        }
    }

    /** Opens a {@code villager_death} situation when an MCA villager with a home village dies (0.8.0). */
    @SubscribeEvent
    public static void onVillagerDeathSituation(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !McaCompat.isMcaVillager(event.getEntity())) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            return;
        }
        SituationDetectors.onVillagerDeath(server, (ServerLevel) event.getEntity().level(), event.getEntity());
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        // A canceled interaction never happened, so it never counts (QuestEventHandlers.onEntityInteract).
        if (event.isCanceled()) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            forActiveObjectives(player, BreakBlockObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(event.getState())) {
                            progress.add(1);
                        }
                    });
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        // Per-tick: keep led escortees moving smoothly. The walk target must be re-issued every tick so
        // MCA's per-tick brain behaviors can't steal it; the once-per-second poll below can't keep up.
        forActiveObjectives(player, EscortEntityObjective.class,
                (objective, active, progress) -> objective.drive(player, active, progress, level));
        if (player.tickCount % 20 != 0) {
            return; // ~once per second is plenty for location checks
        }
        forActiveObjectives(player, VisitBiomeObjective.class,
                (objective, progress) -> {
                    if (progress.count() == 0 && objective.matches(player)) {
                        progress.setCount(1);
                    }
                });
        forActiveObjectives(player, VisitDimensionObjective.class,
                (objective, progress) -> {
                    if (progress.count() == 0 && objective.matches(player)) {
                        progress.setCount(1);
                    }
                });
        forActiveObjectives(player, EscortEntityObjective.class,
                (objective, active, progress) -> objective.poll(player, active, progress, level));
        forActiveObjectives(player, ProtectEntityObjective.class,
                (objective, active, progress) -> objective.poll(player, active, progress, level));
        forActiveObjectives(player, CureVillagerObjective.class,
                (objective, active, progress) -> objective.poll(player, active, progress, level));
        forActiveObjectives(player, EnterStructureObjective.class,
                (objective, active, progress) -> objective.poll(player, progress, level));
        forActiveObjectives(player, ReachLocationObjective.class,
                (objective, active, progress) -> objective.poll(player, active, progress, level));
        // Generic poll pass (spec section 11.3): any objective type implementing PollingObjective is
        // picked up here automatically, after all built-ins above, so this is a pure additive extension
        // point — new poll-driven objective types no longer require editing this method.
        //
        // Townstead-backed objectives are additionally gated on townsteadPollIntervalTicks. That key had
        // promised since 1.4.0 that it "shares the existing once-per-second objective pass", and this pass
        // had never read it; a server owner raising it to trade responsiveness for tick time got nothing.
        // At its default of 20 the gate opens every second, so nothing changes for anyone who left it be.
        boolean townsteadDue = townsteadPollDue(player, level);
        forActiveObjectives(player, PollingObjective.class,
                (objective, active, progress) -> {
                    if (objective instanceof TownsteadObjective && !townsteadDue) {
                        return;
                    }
                    objective.poll(player, active, progress);
                });
        highlightTargets(player, level);
        accrueSuspendedTime(player);
        checkFailureTriggers(player);
        autoCompleteSelfQuests(player);
        maybeScanSituations(player);
        maybePollProjects(player);
        maybeRetryBankedRewards(player, level);
        QuestManager.checkReadyTransitions(player);
        // Refresh the client quest log + HUD (~once per second) for players with active quests.
        QuestCapabilities.get(player).ifPresent(data -> {
            if (!data.active().isEmpty()) {
                QuestManager.syncLog(player);
            }
        });
    }

    /** Game time of the last server-wide situation scan, so the per-player tick runs it only once per interval. */
    private static long lastSituationScan = Long.MIN_VALUE;
    /** Sibling of {@link #lastSituationScan} for the project sweep; see {@link #due}. */
    private static long lastProjectPoll;

    /**
     * Runs the periodic situation detector sweep at most once per {@code situationDetectionIntervalTicks},
     * server-wide. Driven from the per-player tick (which already throttles to ~1/sec); the game-time guard
     * collapses the many per-player calls in a tick down to a single scan.
     */
    private static void maybeScanSituations(ServerPlayer player) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (!due(now, lastSituationScan, McaQuestsConfig.COMMON.situationDetectionIntervalTicks.get())) {
            return;
        }
        lastSituationScan = now;
        SituationDetectors.scan(server);
        SituationManager.tick(server);
    }

    /** Game time each player's Townstead objectives were last read, so the interval is honoured per player. */
    private static final Map<UUID, Long> lastTownsteadPoll = new HashMap<>();

    /**
     * Whether this player's Townstead-backed objectives are due to re-read villager state.
     *
     * <p>Per player rather than server-wide, because these objectives read the state of the villager
     * <em>that player's</em> quest is about; a shared clock would let one player's tick consume another's
     * turn and leave a quest sitting still.
     */
    private static boolean townsteadPollDue(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        Long last = lastTownsteadPoll.get(player.getUUID());
        if (last != null && !due(now, last, McaQuestsConfig.COMMON.townsteadPollIntervalTicks.get())) {
            return false;
        }
        lastTownsteadPoll.put(player.getUUID(), now);
        return true;
    }

    /**
     * Whether a server-wide pass is due, tolerating world time that has gone <em>backwards</em>.
     *
     * <p>These guards are static and survive a world change, so in single-player, loading a younger
     * world leaves {@code last} in that world's future: a plain {@code now - last >= interval} then
     * reads negative and the pass silently never runs again until the new world catches up, which for a
     * fresh world means effectively never. Treating any backwards jump as "due" resets it on the first
     * tick instead.
     */
    private static boolean due(long now, long last, long interval) {
        return now < last || now - last >= interval;
    }

    /**
     * Advances project objectives that watch the world rather than waiting to be told about it.
     *
     * <p>Hung off the player tick because that is the only server tick this mod subscribes to, and
     * adding a second subscriber for one bounded sweep would be worse. The pass itself is server-wide
     * and player-independent -- it runs once per interval no matter how many players are online -- so
     * hosting it here is a scheduling detail, not a scoping one.
     */
    private static void maybePollProjects(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (!due(now, lastProjectPoll, McaQuestsConfig.COMMON.townsteadProjectPollIntervalTicks.get())) {
            return;
        }
        lastProjectPoll = now;
        ProjectManager.pollProjects(server);
    }

    /** In-game day (per player) the banked-reward retry last ran, so it fires at most once per day (task M3.1). */
    private static final Map<UUID, Long> lastBankedRetryDay = new HashMap<>();

    /**
     * Retries any banked FTB-claim rewards (village_reputation / hearts / grant_title with no resolvable
     * target at claim time — spec 1.0.0 §16, task M3.1) at most once per in-game day while the player is
     * online, piggybacking this existing ~once/sec-throttled tick rather than adding new tick machinery
     * (user decision). Login already retries via {@code ProjectLifecycleEvents.onPlayerLogin} →
     * {@code ProjectManager.deliverPending}; this covers the case of a long, uninterrupted session where
     * a village/spouse only becomes reachable well after login.
     */
    private static void maybeRetryBankedRewards(ServerPlayer player, ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        Long lastDay = lastBankedRetryDay.get(player.getUUID());
        if (lastDay != null && lastDay == day) {
            return;
        }
        lastBankedRetryDay.put(player.getUUID(), day);
        ProjectManager.deliverPending(player);
    }

    /**
     * Fails active quests whose {@code failure} triggers have fired: a relative {@code deadline_ticks}
     * budget elapsed, a {@code deadline_time} time-of-day window passed, or required weather stopped.
     * Time deadlines are derived from the persisted {@code startGameTime}, so they survive logout /
     * restart. A quest whose objectives are already satisfied is left untouched — the player keeps a
     * grace window to turn it in. Each failure routes through {@link QuestManager#failQuest} so the
     * configured outcome (hearts / retry cooldown / dialogue) and the FAILED history record apply
     * uniformly with every other failure path.
     */
    /** One polling pass, in ticks: the cadence {@code onPlayerTick} throttles its ~1 Hz block to. */
    private static final long POLL_INTERVAL_TICKS = 20L;

    /**
     * Adds this pass's worth of ticks to every quest that is currently unplayable, so a deadline does not
     * run down while the mod one of its objectives reads is uninstalled (Townstead spec 10.1).
     *
     * <p>Called once per pass, before the failure check, and deliberately not from
     * {@link #forActiveObjectives} — that runs once per objective type, so accruing there would count the
     * same second several times over.
     */
    private static void accrueSuspendedTime(ServerPlayer player) {
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    if (QuestManager.isSuspended(player, active.resolve(base), active)) {
                        active.addSuspendedTicks(POLL_INTERVAL_TICKS);
                    }
                });
            }
        });
    }

    private static void checkFailureTriggers(ServerPlayer player) {
        QuestCapabilities.get(player).ifPresent(data -> {
            long now = player.level().getGameTime();
            List<FailedTrigger> toFail = new ArrayList<>();
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    QuestDefinition def = active.resolve(base);
                    def.failure().ifPresent(failure -> {
                        if (QuestManager.isComplete(player, def, active)) {
                            return; // ready to turn in — never failed by a time/weather trigger
                        }
                        // Checked BEFORE the suspension guard, and it is the only trigger that is: a
                        // lost target is itself a suspension, so guarding first would make the opt-in
                        // unreachable and the quest would wait forever for someone who is never coming.
                        if (failure.failOnTargetLost()
                                && QuestManager.hasLostBoundTarget(player, def, active)) {
                            toFail.add(new FailedTrigger(active, def, QuestFailedEvent.Reason.TARGET_LOST));
                            return;
                        }
                        if (QuestManager.isSuspended(player, def, active)) {
                            return; // unplayable right now — its clock is frozen, so nothing can expire
                        }
                        QuestFailedEvent.Reason reason = firedReason(player, active, failure, now);
                        if (reason != null) {
                            toFail.add(new FailedTrigger(active, def, reason));
                        }
                    });
                });
            }
            for (FailedTrigger ft : toFail) {
                QuestManager.failQuest(player, ft.active(), ft.def(), ft.reason(), null, data);
            }
        });
    }

    /** The first failure trigger that has fired for {@code active}, or {@code null} if none has. */
    @Nullable
    private static QuestFailedEvent.Reason firedReason(ServerPlayer player, ActiveQuest active,
                                                       FailureSpec failure, long now) {
        ServerLevel level = (ServerLevel) player.level();
        OptionalLong deadline = failure.deadlineGameTime(active.startGameTime(), active.startDayTime(),
                now, level.getDayTime());
        // Time spent suspended does not count against the deadline (Townstead spec 10.1). The offset is
        // applied to "now" rather than to the start, because a deadline_time_of_day is anchored on the
        // time of day the quest was accepted -- moving the start would retarget it to a different hour
        // instead of merely postponing it.
        if (deadline.isPresent() && active.effectiveNow(now) >= deadline.getAsLong()) {
            return failure.timeDeadlineReason();
        }
        if (failure.requireWeather().isPresent() && !failure.requireWeather().get().matches(level)) {
            return QuestFailedEvent.Reason.WEATHER;
        }
        return null;
    }

    private record FailedTrigger(ActiveQuest active, QuestDefinition def, QuestFailedEvent.Reason reason) {
    }

    /** Turns in SELF_COMPLETE quests as soon as their objectives are satisfied (spec section 17). */
    private static void autoCompleteSelfQuests(ServerPlayer player) {
        QuestCapabilities.get(player).ifPresent(data -> {
            List<ActiveQuest> ready = new ArrayList<>();
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    QuestDefinition def = active.resolve(base);
                    if (def.turnIn().mode() == TurnInMode.SELF_COMPLETE && !active.rewardClaimed()
                            && QuestManager.isComplete(player, def, active)) {
                        ready.add(active);
                    }
                });
            }
            ready.forEach(active -> QuestManager.selfComplete(player, active));
        });
    }

    /**
     * When a quest giver dies, fail the affected quests. A quest fails if its {@code failure} block
     * opts in with {@code fail_on_giver_death}, OR (legacy global behaviour) if the
     * {@code failQuestIfGiverDies} config is on and the quest is turned in to its original giver. All
     * failures route through {@link QuestManager#failQuest} (spec section 17).
     */
    @SubscribeEvent
    public static void onGiverDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !McaCompat.isMcaVillager(event.getEntity())) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            return;
        }
        boolean globalFail = McaQuestsConfig.COMMON.failQuestIfGiverDies.get();
        Entity giverEntity = event.getEntity();
        UUID giver = giverEntity.getUUID();
        // Written down for the players who are not here to see it: login reads this back and applies
        // exactly the rule below (F-B03). Recorded unconditionally, because whether a quest cares about
        // the death is a per-quest question this villager knows nothing about.
        DeadGiversData.get(server).record(giver, giverEntity.level().getGameTime());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestCapabilities.get(player).ifPresent(data -> {
                List<ActiveQuest> failed = new ArrayList<>();
                for (ActiveQuest active : data.byVillager(giver)) {
                    QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                        QuestDefinition def = active.resolve(base);
                        if (def.turnIn().mode().failsOnGiverDeath(def.failure(), globalFail)) {
                            failed.add(active);
                        }
                    });
                }
                for (ActiveQuest active : failed) {
                    QuestDefinitions.resolve(active.questId()).ifPresent(base ->
                            QuestManager.failQuest(player, active, active.resolve(base),
                                    QuestFailedEvent.Reason.GIVER_DIED, giverEntity, data));
                }
                if (!failed.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("mcaquests.message.giver_died"));
                }
            });
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack crafted = event.getCrafting();
            forActiveObjectives(player, CraftItemObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(crafted)) {
                            progress.add(crafted.getCount());
                        }
                    });
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        // A canceled interaction never happened, so it never counts (QuestEventHandlers.onEntityInteract).
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState placed = event.getPlacedBlock();
            BlockPos pos = event.getPos();
            ServerLevel level = (ServerLevel) player.level();
            forActiveObjectives(player, PlaceBlockObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(placed)) {
                            progress.add(1);
                        }
                    });
            forActiveObjectives(player, BuildNearLocationObjective.class,
                    (objective, active, progress) -> objective.onPlace(player, active, progress, placed, pos, level));
        }
    }

    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbstractVillager merchant = event.getAbstractVillager();
        if (merchant.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) merchant.level();
        forActiveObjectives(player, TradeWithVillagerObjective.class,
                (objective, active, progress) -> objective.onTrade(player, active, progress, merchant, level));
    }

    @SubscribeEvent
    public static void onAnimalTamed(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) {
            return;
        }
        Animal animal = event.getAnimal();
        if (animal.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) animal.level();
        forActiveObjectives(player, TameAnimalObjective.class,
                (objective, active, progress) -> objective.onTame(player, active, progress, animal, level));
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) {
            return;
        }
        AgeableMob child = event.getChild();
        if (child == null || child.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) child.level();
        forActiveObjectives(player, BreedAnimalsObjective.class,
                (objective, active, progress) -> objective.onBreed(player, active, progress, child, level));
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSleepingLongEnough()) {
                forActiveObjectives(player, SleepOrRestObjective.class,
                        (objective, progress) -> progress.setCount(1));
            }
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            forActiveObjectives(player, FishItemObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(drop)) {
                            progress.add(drop.getCount());
                        }
                    });
        }
    }

    /**
     * Item-driven villager objectives (deliver / heal / cure). Unlike a conversation these are meant to
     * happen with something in hand, so there is no empty-hand gate here — and MCA: Quests never cancels
     * the interaction, so MCA's own handling of the same click still runs.
     *
     * <p>Conversation credit is <em>not</em> done here: {@link QuestEventHandlers} owns that decision so
     * the empty-hand / non-canceled gate lives in exactly one place.
     */
    @SubscribeEvent
    public static void onTalkToVillager(PlayerInteractEvent.EntityInteract event) {
        // A canceled interaction never happened, so it never counts (QuestEventHandlers.onEntityInteract).
        if (event.isCanceled()) {
            return;
        }
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !McaCompat.isMcaVillager(event.getTarget())) {
            return;
        }
        if (event.getTarget() instanceof LivingEntity villager) {
            ServerLevel level = (ServerLevel) event.getLevel();
            ItemStack held = player.getMainHandItem();
            forActiveObjectives(player, HealEntityObjective.class,
                    (objective, active, progress) -> objective.onInteract(player, active, progress, villager, held, level));
            forActiveObjectives(player, CureVillagerObjective.class,
                    (objective, active, progress) -> objective.onInteract(player, active, progress, villager, held, level));
            forActiveObjectives(player, DeliverToVillagerObjective.class,
                    (objective, active, progress) -> objective.onInteract(player, active, progress, villager, level));
        }
    }

    /**
     * Credits one conversation with {@code villager} to every active {@code talk_to_profession} objective
     * whose profession matches under the configured matching mode.
     *
     * <p>Counts <b>distinct</b> villagers: the villager's UUID is recorded on the objective's progress and
     * a repeat visit to the same villager never advances it again. That is what makes the credit idempotent,
     * so the base interaction hook and an MCA: Conversations signal for the same conversation cannot both
     * count. Progress is settled (self-complete → ready → sync) straight away rather than waiting for the
     * next per-second tick.
     */
    public static void creditTalk(ServerPlayer player, Entity villager) {
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        UUID villagerUuid = villager.getUUID();
        boolean[] advanced = {false};
        forActiveObjectives(player, TalkToProfessionObjective.class,
                (objective, progress) -> {
                    if (progress.count() >= objective.required()) {
                        return; // already satisfied
                    }
                    if (!objective.matches(profession)) {
                        QuestEventHandlers.debugReject("profession mismatch (wanted " + objective.profession()
                                + ", villager is " + profession + ")", villager);
                        return;
                    }
                    if (!progress.markTalkedTo(villagerUuid)) {
                        QuestEventHandlers.debugReject("duplicate villager — already counted", villager);
                        return;
                    }
                    progress.add(1);
                    advanced[0] = true;
                });
        if (advanced[0]) {
            QuestManager.settleProgress(player);
        }
    }

    /** Applies {@code action} to each active-quest objective of {@code type} with its progress. */
    private static <T extends QuestObjective> void forActiveObjectives(
            ServerPlayer player, Class<T> type, BiConsumer<T, ObjectiveProgress> action) {
        forActiveObjectives(player, type, (objective, active, progress) -> action.accept(objective, progress));
    }

    /**
     * Applies {@code action} to each active-quest objective of {@code type} with its owning
     * {@link ActiveQuest} and progress. The NPC/village objectives need the {@code ActiveQuest} to
     * resolve the giver → villager/anchor (the giver UUID lives on the snapshot).
     */
    static <T extends QuestObjective> void forActiveObjectives(
            ServerPlayer player, Class<T> type, ObjectiveAction<T> action) {
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    // Resolve template values so progress is tracked against this copy's concrete objectives.
                    ServerLevel level = (ServerLevel) player.level();
                    List<QuestObjective> objectives = active.resolve(base).objectives();
                    for (int i = 0; i < objectives.size(); i++) {
                        QuestObjective objective = objectives.get(i);
                        if (!type.isInstance(objective)) {
                            continue;
                        }
                        ObjectiveProgress progress = active.progress(i);
                        // Skip per objective, not per quest: a Townstead objective sitting beside an
                        // ordinary "bring me six loaves" must not freeze the loaves too. This is also the
                        // one guard covering event credit as well as polling, since both route through here.
                        if (objective.unavailableReason(player, active, progress, level).isPresent()) {
                            continue;
                        }
                        action.accept(type.cast(objective), active, progress);
                    }
                });
            }
        });
    }

    /** Callback that also receives the owning {@link ActiveQuest} (for giver/villager resolution). */
    @FunctionalInterface
    interface ObjectiveAction<T extends QuestObjective> {
        void accept(T objective, ActiveQuest active, ObjectiveProgress progress);
    }
}
