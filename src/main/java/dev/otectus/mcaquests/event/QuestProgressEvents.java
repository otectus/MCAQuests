package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.objective.BreakBlockObjective;
import dev.otectus.mcaquests.quest.objective.CraftItemObjective;
import dev.otectus.mcaquests.quest.objective.FishItemObjective;
import dev.otectus.mcaquests.quest.objective.KillEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.PlaceBlockObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.TalkToProfessionObjective;
import dev.otectus.mcaquests.quest.objective.VisitBiomeObjective;
import dev.otectus.mcaquests.quest.objective.VisitDimensionObjective;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
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
            forActiveObjectives(player, KillEntityObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(event.getEntity())) {
                            progress.add(1);
                        }
                    });
        }
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
        checkFailureTriggers(player);
        autoCompleteSelfQuests(player);
        QuestManager.checkReadyTransitions(player);
        // Refresh the client quest log + HUD (~once per second) for players with active quests.
        QuestCapabilities.get(player).ifPresent(data -> {
            if (!data.active().isEmpty()) {
                QuestManager.syncLog(player);
            }
        });
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
                QuestRegistry.get(active.questId()).ifPresent(base -> {
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
                QuestRegistry.get(active.questId()).ifPresent(base -> {
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
                    QuestRegistry.get(active.questId()).ifPresent(base -> {
                        QuestDefinition def = active.resolve(base);
                        boolean perQuest = def.failure().map(FailureSpec::failOnGiverDeath).orElse(false);
                        boolean globalApplies = globalFail && def.turnIn().mode() == TurnInMode.ORIGINAL_GIVER;
                        if (perQuest || globalApplies) {
                            failed.add(active);
                        }
                    });
                }
                for (ActiveQuest active : failed) {
                    QuestRegistry.get(active.questId()).ifPresent(base ->
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
            forActiveObjectives(player, PlaceBlockObjective.class,
                    (objective, progress) -> {
                        if (objective.matches(event.getPlacedBlock())) {
                            progress.add(1);
                        }
                    });
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
    }

    /** Applies {@code action} to each active-quest objective of {@code type} with its progress. */
    private static <T extends QuestObjective> void forActiveObjectives(
            ServerPlayer player, Class<T> type, BiConsumer<T, ObjectiveProgress> action) {
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestRegistry.get(active.questId()).ifPresent(base -> {
                    // Resolve template values so progress is tracked against this copy's concrete objectives.
                    List<QuestObjective> objectives = active.resolve(base).objectives();
                    for (int i = 0; i < objectives.size(); i++) {
                        QuestObjective objective = objectives.get(i);
                        if (type.isInstance(objective)) {
                            action.accept(type.cast(objective), active.progress(i));
                        }
                    }
                });
            }
        });
    }
}
