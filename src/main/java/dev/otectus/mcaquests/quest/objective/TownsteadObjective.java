package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * An objective that reads Townstead state, and therefore cannot be evaluated when Townstead is absent
 * or when the specific capability it needs did not bind (Townstead spec §10.1).
 *
 * <p>Implementing this is the whole of what an objective must do to be save-safe. The suspension
 * behaviour — keep progress, stop polling, never auto-fail, never read as complete, show the reason,
 * stay abandonable, resume the original baseline — falls out of
 * {@link #unavailableReason} being consulted at the four points where a quest could otherwise advance
 * or die.
 *
 * <p><b>Capability-precise, not mod-precise.</b> An objective declares only what it actually reads, so
 * a Townstead point release that moved one internal method suspends the handful of quests that needed
 * it rather than every Townstead quest in the world.
 *
 * <p>Suspension is recomputed every pass rather than persisted. A stored flag would need migrating,
 * could go stale against a capability set that changed underneath it, and would have to be cleared by
 * something — where "ask the bridge" is always current and needs no bookkeeping at all.
 */
public interface TownsteadObjective extends QuestObjective {

    /**
     * Every capability this objective reads. All of them must be bound for it to run; a missing one
     * suspends the quest rather than failing it.
     */
    Set<TownsteadCapability> requiredCapabilities();

    @Override
    default Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return Optional.of(Component.translatable("mcaquests.objective.townstead.unavailable"));
        }
        for (TownsteadCapability capability : requiredCapabilities()) {
            if (!bridge.has(capability)) {
                return Optional.of(Component.translatable(
                        "mcaquests.objective.townstead.capability_missing"));
            }
        }
        return Optional.empty();
    }

    /**
     * Take the starting reading, once, at the moment the quest is accepted.
     *
     * <p>Called from {@code QuestManager.accept} rather than left to the first poll, because a second
     * of gameplay is long enough to eat: a player who accepts "feed them back up" and immediately hands
     * over bread would otherwise have that bread counted as the starting state and lose the credit for
     * it. Objectives with nothing to freeze ignore this.
     */
    default void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                                ServerLevel level) {
    }

    /** Convenience for the objective's own guards: true when every declared capability is available. */
    default boolean townsteadReady() {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return false;
        }
        for (TownsteadCapability capability : requiredCapabilities()) {
            if (!bridge.has(capability)) {
                return false;
            }
        }
        return true;
    }
}
