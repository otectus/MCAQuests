package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common plumbing for the mcaquests boolean FTB Quests tasks (spec §15.0). Subclasses implement
 * {@link #check(ServerPlayer)} with their real, possibly-throwing logic; this base wraps every
 * call in the mod-wide fail-safe contract (§10.2) and wires the poll cadence from config.
 *
 * <p>Verified against {@code AbstractBooleanTask} (line evidence from FTB-Quests {@code v2001.4.22},
 * {@code quest/task/AbstractBooleanTask.java:23-30}; re-verified against the {@code v2101.1.31}
 * bytecode this build compiles against, where only the line numbers differ): the extension point is
 * {@code canSubmit(TeamData, ServerPlayer)}, called from a {@code final}-shaped
 * {@code submitTask(TeamData, ServerPlayer, ItemStack)} override that already gates on
 * {@code !teamData.isCompleted(this) && checkTaskSequence(teamData)} before invoking
 * {@code canSubmit} — we do not need to (and must not try to) duplicate those guards here.
 */
public abstract class McaBooleanTaskBase extends AbstractBooleanTask {

    protected McaBooleanTaskBase(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * The real check, e.g. "is this player married". May throw; {@link #canSubmit} wraps it in the
     * fail-safe contract and defaults to {@code false} on any {@link Throwable}.
     */
    protected abstract boolean check(ServerPlayer player);

    @Override
    public final boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return false;
        }
        try {
            return check(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ task {} check failed", getType().getTypeId(), t);
            return false;
        }
    }

    /**
     * Poll cadence in ticks: {@code ftbqStatePollIntervalTicks}, clamped defensively to the config's
     * own declared range (20-1200) in case a hand-edited config file bypasses Forge's validation.
     */
    @Override
    public int autoSubmitOnPlayerTick() {
        int configured = McaQuestsConfig.COMMON.ftbqStatePollIntervalTicks.get();
        return Math.min(1200, Math.max(20, configured));
    }

    // checkOnLogin() is intentionally not overridden: Task's default is `!consumesResources()`
    // (Task.java:302-304), and none of our tasks accept item/fluid insertion, so it already
    // evaluates to `true` — exactly the "stays default true" behaviour the spec calls for.
}
