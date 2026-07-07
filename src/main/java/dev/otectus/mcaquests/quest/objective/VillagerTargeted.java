package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * An objective whose completion hinges on a specific villager the player must locate — a delivery
 * recipient, or the villager to heal / cure / escort / protect / defend. Implementing this lets
 * {@code QuestProgressEvents} highlight (glow) that villager while the quest is active, so it can be
 * found. Returns {@code empty} when the target is unresolved or not currently loaded (nothing to glow).
 */
public interface VillagerTargeted {

    Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                           ObjectiveProgress progress, ServerLevel level);
}
