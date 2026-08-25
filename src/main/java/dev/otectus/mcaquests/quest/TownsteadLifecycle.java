package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Tells Townstead when a quest changes hands, so villagers can react to it (Townstead spec §7.1).
 *
 * <p><b>Always after the transaction, never during.</b> A reaction is a villager waving; a quest state
 * change is a player's progress. Dispatching only once the quest system has committed means a reaction
 * that throws — or a Townstead that has been removed — can never roll back a completion or lose a
 * reward. Nothing here reports failure upward, because there is nothing useful the caller could do
 * with it.
 *
 * <p>Each phase fires at most once per quest instance, recorded on {@link ActiveQuest} and persisted,
 * so a reconnect or a restart cannot replay the villager's reaction to something that happened days
 * ago.
 */
public final class TownsteadLifecycle {

    /**
     * The phase names Townstead sees. These are a published contract — a Townstead reaction pack keys
     * its bindings off these exact strings — so they are not renamed casually.
     */
    public enum Phase {
        ACCEPTED("accepted"),
        READY("ready"),
        COMPLETED("completed"),
        FAILED("failed"),
        ABANDONED("abandoned");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private TownsteadLifecycle() {
    }

    /**
     * Fires {@code phase} for this quest if it has not fired before.
     *
     * @param giver the villager to react, which may be null when they are dead, unloaded, or in another
     *              dimension — in which case nothing happens, silently, because there is nobody to react
     */
    public static void dispatch(ServerPlayer player, ActiveQuest active, @Nullable Entity giver,
                                Phase phase) {
        if (!McaQuestsConfig.COMMON.townsteadReactionsEnabled.get()) {
            return;
        }
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.has(TownsteadCapability.DISPATCH_REACTION)) {
            return;
        }
        if (!(giver instanceof LivingEntity living) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Marked before dispatching, not after: if the reaction throws, the phase is still spent. A
        // villager who failed to wave once should not wave twice on the next reconnect.
        if (!active.markPhaseDispatched(phase.ordinal())) {
            return;
        }
        bridge.dispatchTransition(level, living, active.questId(), phase.id());
    }
}
