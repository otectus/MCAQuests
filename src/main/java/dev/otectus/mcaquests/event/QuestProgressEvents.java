package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.quest.objective.BreakBlockObjective;
import dev.otectus.mcaquests.quest.objective.KillEntityObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.VisitBiomeObjective;
import dev.otectus.mcaquests.quest.objective.VisitDimensionObjective;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
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
    }

    /** Applies {@code action} to each active-quest objective of {@code type} with its progress. */
    private static <T extends QuestObjective> void forActiveObjectives(
            ServerPlayer player, Class<T> type, BiConsumer<T, ObjectiveProgress> action) {
        QuestCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest active : data.active()) {
                QuestRegistry.get(active.questId()).ifPresent(def -> {
                    List<QuestObjective> objectives = def.objectives();
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
