package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * {@code mcaquests:reputation} (spec §15.3) — a counter task with a twist: the progress bar's target
 * depends on which of the two fields is "active". With {@code village_count == 1} (the common case —
 * "reach N reputation with a village"), the target is {@code reputation} and progress is the best
 * village's current reputation, clamped to it. With {@code village_count > 1} ("reach N reputation with
 * M villages independently"), the target is {@code village_count} and progress is how many villages
 * currently qualify. Both modes are monotone under normal play, but reputation <em>can</em> decrease
 * (situation failures), so — per {@link McaCounterTaskBase}'s monotone high-water rule — a completion,
 * once latched by FTB, stays latched even if reputation later drops. That is intentional FTB semantics
 * for every stat-style task, not a bug: "reach" tasks reward having once reached the goal.
 */
public class McaReputationTask extends McaCounterTaskBase {

    private int reputation = 100;
    private int villageCount = 1;

    public McaReputationTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.REPUTATION;
    }

    @Override
    protected boolean usesSharedCount() {
        return false; // target is `reputation` or `village_count`, never the shared `count` field.
    }

    @Override
    protected long targetProgress() {
        return villageCount <= 1 ? reputation : villageCount;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0L;
        }
        // Per-player now: an FTB task asks about THIS player's standing, which is what it always
        // claimed to do even when the underlying number was world-shared (§29.9).
        Map<Integer, Integer> reputations = dev.otectus.mcaquests.quest.reputation.QuestReputation.overworldVillageScores(server, player.getUUID());
        if (villageCount <= 1) {
            int best = 0;
            for (int rep : reputations.values()) {
                if (rep > best) {
                    best = rep;
                }
            }
            return best;
        }
        long qualifying = 0L;
        for (int rep : reputations.values()) {
            if (rep >= reputation) {
                qualifying++;
            }
        }
        return qualifying;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putInt("reputation", reputation);
        nbt.putInt("village_count", villageCount);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        reputation = Math.max(1, nbt.getInt("reputation"));
        villageCount = Math.max(1, nbt.getInt("village_count"));
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(reputation);
        buffer.writeVarInt(villageCount);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        reputation = buffer.readVarInt();
        villageCount = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("reputation", reputation, v -> reputation = v, 100, 1, Integer.MAX_VALUE)
                .setNameKey("ftbquests.task.mcaquests.reputation.reputation");
        config.addInt("village_count", villageCount, v -> villageCount = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("ftbquests.task.mcaquests.reputation.village_count");
    }

    @Override
    public MutableComponent getAltTitle() {
        if (villageCount <= 1) {
            return Component.translatable("ftbquests.task.mcaquests.reputation.alt_title.single", reputation);
        }
        return Component.translatable("ftbquests.task.mcaquests.reputation.alt_title.multi", reputation, villageCount);
    }

    @Override
    public void addMouseOverText(TooltipList list, TeamData teamData) {
        super.addMouseOverText(list, teamData);
        list.add(Component.translatable("ftbquests.task.mcaquests.reputation.progress",
                teamData.getProgress(this), getMaxProgress()));
    }
}
