package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A reward that changes Townstead state (Townstead spec §5.5).
 *
 * <h2>Why a failure here does not, by default, block a turn-in</h2>
 *
 * <p>Because the player has already done the work. If Townstead were uninstalled between accepting a
 * quest and handing it in, refusing the turn-in would leave them holding a finished quest they can
 * never complete and never get paid for — a worse outcome than quietly skipping the villager-facing
 * half of the reward. Server owners who would rather the quest waited can set
 * {@code compat.townstead.rewardFailureBlocksCompletion}, which routes through {@link #canApply}.
 *
 * <h2>Exactly once</h2>
 *
 * <p>Nothing extra is needed for that. {@code QuestManager.completeQuest} claims
 * {@code ActiveQuest.rewardClaimed} — persisted, and set <em>before</em> any reward runs — and returns
 * early if it was already claimed. A duplicate turn-in packet, a reconnect, or a restart all hit that
 * latch, so a Townstead mutation cannot be applied twice.
 */
public interface TownsteadReward extends QuestReward {

    /** Which villager this reward is about. Mutations need exactly one, so never {@code village_any}. */
    TownsteadTarget target();

    /** The capability this reward needs, so a missing one is reported rather than silently skipped. */
    TownsteadCapability capability();

    /** The server-owner switch that gates this kind of reward. */
    boolean enabledByConfig();

    /**
     * True when this reward could be applied right now. Consulted only when the server has asked for
     * reward failures to block completion; otherwise a failure is logged and the turn-in proceeds.
     */
    default boolean canApply(ServerPlayer player, @Nullable Entity villager) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        return bridge.isAvailable() && bridge.has(capability())
                && resolveTarget(player, villager).isPresent();
    }

    /** The villager to act on, or empty when it cannot be resolved. */
    default Optional<Entity> resolveTarget(ServerPlayer player, @Nullable Entity villager) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return TownsteadTargetResolver.resolveForOffer(target(), player, villager, level);
    }

    /**
     * One log line for a reward that could not be applied, at DEBUG so a server that has simply removed
     * Townstead does not fill its log with one line per completed quest.
     */
    default void reportFailure(TownsteadMutationResult result) {
        McaQuests.LOGGER.debug("[MCA: Quests] Townstead reward {} did not apply ({}); the quest completed "
                + "regardless.", type().id(), result.reason());
    }
}
