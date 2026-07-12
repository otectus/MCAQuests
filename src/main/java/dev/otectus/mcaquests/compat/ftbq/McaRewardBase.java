package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common plumbing for the mcaquests FTB Quests reward types (spec §16 intro). Subclasses implement
 * {@link #doClaim(ServerPlayer, boolean)} with their real, possibly-throwing claim logic; this base:
 *
 * <ul>
 *   <li>early-returns a one-time-WARN no-op when {@code !FtbqBridge.Holder.get().isAvailable()}. Unlike
 *       task registration (never gated), {@code isAvailable()} also folds in
 *       {@code enableFtbQuestsIntegration}, which can flip false at runtime via config reload even
 *       though the reward <em>type</em> stays registered (§2/§4.3: registration is never gated) — so a
 *       claim can genuinely reach here while the bridge is off. One flag shared across all three reward
 *       types, not one per type: it is the same "integration unavailable" condition regardless of which
 *       reward tripped it, so one log line suffices — the same "not one per check" reasoning
 *       {@code McaReputationTierTask} documents for its own WARN-once set.</li>
 *   <li>wraps {@link #doClaim} in the mod-wide fail-safe contract (§10.2): a {@link Throwable} is logged
 *       at WARN and — per §16's "a failed claim logs WARN and messages the player when notify" — the
 *       player is told the claim failed, but only when {@code notify} (mirrors FTB's own autoclaim/
 *       claim-all paths, which claim with {@code notify=false} and expect silence).</li>
 * </ul>
 *
 * <p>Per-player/per-team claiming is left entirely to FTB's own {@code Reward} tristate (untouched here,
 * per the task's constraint) — this base only ever runs once {@code Reward.claim} is already being
 * invoked for the right recipient.
 */
public abstract class McaRewardBase extends Reward {

    private static final AtomicBoolean UNAVAILABLE_WARNED = new AtomicBoolean(false);

    protected McaRewardBase(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * The real claim logic, e.g. resolving a village and awarding reputation. May throw; {@link #claim}
     * wraps it in the fail-safe contract and defaults to "nothing happened" on any {@link Throwable}.
     */
    protected abstract void doClaim(ServerPlayer player, boolean notify);

    @Override
    public final void claim(ServerPlayer player, boolean notify) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            if (UNAVAILABLE_WARNED.compareAndSet(false, true)) {
                McaQuests.LOGGER.warn(
                        "[MCA: Quests] FTBQ reward {} claimed while the integration is unavailable; no-op.",
                        getType().getTypeId());
            }
            return;
        }
        try {
            doClaim(player, notify);
        } catch (Throwable t) {
            McaQuests.LOGGER.warn("[MCA: Quests] FTBQ reward {} claim failed for {}",
                    getType().getTypeId(), player.getGameProfile().getName(), t);
            if (notify) {
                player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.claim_failed"));
            }
        }
    }
}
