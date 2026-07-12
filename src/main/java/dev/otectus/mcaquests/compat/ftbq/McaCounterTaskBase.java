package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Common plumbing for the mcaquests counter FTB Quests tasks (spec §15.0). Subclasses implement
 * {@link #currentValue(ServerPlayer)} with their real, possibly-throwing computation of "how far
 * along is this player"; this base clamps it to {@link #getMaxProgress()}, applies the §14.1
 * monotone high-water rule (never {@code addProgress}), and owns the shared {@code count} config
 * field + its serialization.
 *
 * <p>Verified against {@code StatTask} (FTB-Quests {@code v2001.4.22},
 * {@code quest/task/StatTask.java:104-123}): the extension point is the 3-arg
 * {@code submitTask(TeamData, ServerPlayer, ItemStack)} override — the 2-arg
 * {@code submitTask(TeamData, ServerPlayer)} overload is {@code final} on {@code Task}
 * ({@code quest/task/Task.java:288-290}, delegates to the 3-arg form with
 * {@code ItemStack.EMPTY}), so it cannot be the override point despite the brief's tentative
 * phrasing. Also mirrors {@code KillTask}/{@code StatTask}'s {@code long value = getMaxProgress()}
 * shape ({@code quest/task/KillTask.java:31,43-45}) for the {@code count} field.
 */
public abstract class McaCounterTaskBase extends Task {

    protected long count = 1;

    protected McaCounterTaskBase(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * The submitting player's current progress toward {@link #targetProgress()}, e.g. a summed
     * completion count. May throw; {@link #submitTask} wraps it in the fail-safe contract and
     * defaults to {@code 0} on any {@link Throwable}.
     */
    protected abstract long currentValue(ServerPlayer player);

    /**
     * The task's completion target = {@link #getMaxProgress()}. Defaults to the shared
     * {@link #count} field; subclasses whose target is a different field override this — e.g.
     * §15.3's {@code McaReputationTask}, whose target is {@code reputation} when
     * {@code village_count == 1} and {@code village_count} otherwise (dual-mode progress bar).
     * Such subclasses should also override {@link #usesSharedCount()} to hide the unused
     * {@code count} row from the editor.
     *
     * <p>The orchestration in {@link #submitTask} stays sealed either way: whatever this returns,
     * progress is clamped to it and written monotone high-water (§14.1) — which is also exactly
     * what latched FTB completion needs when the underlying value can <em>decrease</em> (§15.3:
     * reputation drops never lower recorded progress, and completion, once set, stays set).
     */
    protected long targetProgress() {
        return count;
    }

    /**
     * Whether this task's target is the shared {@link #count} field. When {@code false}, the
     * {@code count} editor row is suppressed (the field still round-trips through disk/net
     * serialization unchanged, keeping the wire format identical across all counter tasks).
     */
    protected boolean usesSharedCount() {
        return true;
    }

    @Override
    public final long getMaxProgress() {
        return targetProgress();
    }

    @Override
    public final void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        // Same guard StatTask applies before recomputing (quest/task/StatTask.java:107-109): skip
        // already-completed tasks and respect the quest's sequential-task ordering.
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData)) {
            return;
        }
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        long value;
        try {
            value = currentValue(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ task {} currentValue failed", getType().getTypeId(), t);
            value = 0L;
        }
        long clamped = Math.min(value, getMaxProgress());
        // §14.1 monotone recompute-from-state: never addProgress from events.
        if (clamped > teamData.getProgress(this)) {
            teamData.setProgress(this, clamped);
        }
    }

    /** Event push is primary for counters; poll (4x the boolean-task interval) is the safety net. */
    @Override
    public int autoSubmitOnPlayerTick() {
        int configured = McaQuestsConfig.COMMON.ftbqStatePollIntervalTicks.get();
        return 4 * Math.min(1200, Math.max(20, configured));
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putLong("count", count);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        count = Math.max(1, nbt.getLong("count"));
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarLong(count);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        count = buffer.readVarLong();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        if (usesSharedCount()) {
            // Per-type name key (§15.0/§22 convention) — getType() dispatches to the leaf class,
            // so e.g. quest_completed gets "ftbquests.task.mcaquests.quest_completed.count".
            // Same shared-field-with-setNameKey idiom as Task's optional_task (Task.java:337).
            config.addLong("count", count, v -> count = v, 1L, 1L, Long.MAX_VALUE)
                    .setNameKey("ftbquests.task.mcaquests." + getType().getTypeId().getPath() + ".count");
        }
    }
}
