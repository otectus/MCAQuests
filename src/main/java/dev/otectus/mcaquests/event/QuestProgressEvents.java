package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestManager;
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
import java.util.List;
import java.util.OptionalLong;
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
            QuestManager.syncLog(player);
        }
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // Credit the responsible player, whether melee or via a projectile they fired.
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
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
                                && protect.matchesTarget(dead, player, active, level)) {
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
     * Glows the villager each active, incomplete quest objective wants the player to find (a delivery
     * recipient, or a villager to heal/cure/escort/protect/defend), so they can be located. Re-applied each
     * ~second, so it persists while the quest is active and lapses shortly after it ends. Gated by the
     * {@code highlightQuestTargets} config.
     */
    private static void highlightTargets(ServerPlayer player, ServerLevel level) {
        if (!McaQuestsConfig.COMMON.highlightQuestTargets.get()) {
            return;
        }
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                    List<QuestObjective> objectives = active.resolve(base).objectives();
                    for (int i = 0; i < objectives.size(); i++) {
                        if (objectives.get(i) instanceof VillagerTargeted targeted) {
                            targeted.highlightTarget(player, active, active.progress(i), level)
                                    .ifPresent(QuestProgressEvents::glow);
                        }
                    }
                });
            }
        });
    }

    /** Applies a brief, particle-free Glowing so the target shows through walls; refreshed by the poll. */
    private static void glow(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
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
        highlightTargets(player, level);
        checkFailureTriggers(player);
        autoCompleteSelfQuests(player);
        maybeScanSituations(player);
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
        if (now - lastSituationScan < McaQuestsConfig.COMMON.situationDetectionIntervalTicks.get()) {
            return;
        }
        lastSituationScan = now;
        SituationDetectors.scan(server);
        SituationManager.tick(server);
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
        OptionalLong deadline = failure.deadlineGameTime(active.startGameTime());
        if (deadline.isPresent() && now >= deadline.getAsLong()) {
            return failure.timeDeadlineReason();
        }
        if (failure.requireWeather().isPresent()
                && !failure.requireWeather().get().matches((ServerLevel) player.level())) {
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestCapabilities.get(player).ifPresent(data -> {
                List<ActiveQuest> failed = new ArrayList<>();
                for (ActiveQuest active : data.byVillager(giver)) {
                    QuestDefinitions.resolve(active.questId()).ifPresent(base -> {
                        QuestDefinition def = active.resolve(base);
                        boolean perQuest = def.failure().map(FailureSpec::failOnGiverDeath).orElse(false);
                        boolean globalApplies = globalFail && def.turnIn().mode() == TurnInMode.ORIGINAL_GIVER;
                        if (perQuest || globalApplies) {
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

    @SubscribeEvent
    public static void onTalkToVillager(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !McaCompat.isMcaVillager(event.getTarget())) {
            return;
        }
        ResourceLocation profession = McaCompat.getProfessionId(event.getTarget()).orElse(null);
        forActiveObjectives(player, TalkToProfessionObjective.class,
                (objective, progress) -> {
                    if (progress.count() < objective.required() && objective.matches(profession)) {
                        progress.add(1);
                    }
                });
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
                    List<QuestObjective> objectives = active.resolve(base).objectives();
                    for (int i = 0; i < objectives.size(); i++) {
                        QuestObjective objective = objectives.get(i);
                        if (type.isInstance(objective)) {
                            action.accept(type.cast(objective), active, active.progress(i));
                        }
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
