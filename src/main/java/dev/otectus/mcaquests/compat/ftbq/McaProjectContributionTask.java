package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.ProgressionStats;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * {@code mcaquests:project_contribution} (spec §15.7) — units contributed (items donated / kills /
 * blocks / talks, whatever the project's objectives bank) to village community projects. Reads
 * {@link ProgressionStats#projectContributions()}: a specific {@code project_id} looks up that project's
 * banked total for the player, empty sums every project. Default {@code count} is 64 (not 1, since a
 * meaningful contribution target is usually a larger unit count than "did it once").
 */
public class McaProjectContributionTask extends McaCounterTaskBase {

    private String projectId = "";

    public McaProjectContributionTask(long id, Quest quest) {
        super(id, quest);
        count = 64;
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.PROJECT_CONTRIBUTION;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        if (data.isEmpty()) {
            return 0L;
        }
        Map<ResourceLocation, Integer> contributions = data.get().stats().projectContributions();
        if (projectId.isEmpty()) {
            return ProgressionStats.total(contributions);
        }
        ResourceLocation id = ResourceLocation.tryParse(projectId);
        if (id == null) {
            return 0L;
        }
        return ProgressionStats.count(contributions, id);
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("project_id", projectId);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        projectId = nbt.getString("project_id");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(projectId, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        projectId = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("project_id", projectId, v -> projectId = v, "")
                .setNameKey("ftbquests.task.mcaquests.project_contribution.project_id");
    }

    @Override
    public MutableComponent getAltTitle() {
        if (!projectId.isEmpty()) {
            return Component.translatable("ftbquests.task.mcaquests.project_contribution.alt_title.specific", count, projectId);
        }
        return Component.translatable("ftbquests.task.mcaquests.project_contribution.alt_title.any", count);
    }
}
