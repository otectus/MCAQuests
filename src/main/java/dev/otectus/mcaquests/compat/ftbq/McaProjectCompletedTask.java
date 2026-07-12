package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.client.ClientKnownIds;
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
 * {@code mcaquests:project_completed} (spec §15.6) — village community projects completed. Reads
 * {@link ProgressionStats#projectCompletions()} (§11.2): a specific {@code project_id} looks up that
 * project's count, empty sums every project.
 *
 * <p>Credit accrues to online participants at the moment a project completes (§11.2) — a player who
 * contributed but logged off before completion is not credited. That is {@code ProgressionStats}' own
 * documented behaviour, unchanged here.
 */
public class McaProjectCompletedTask extends McaCounterTaskBase {

    private String projectId = "";

    public McaProjectCompletedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.PROJECT_COMPLETED;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        if (data.isEmpty()) {
            return 0L;
        }
        Map<ResourceLocation, Integer> completions = data.get().stats().projectCompletions();
        if (projectId.isEmpty()) {
            return ProgressionStats.total(completions);
        }
        ResourceLocation id = ResourceLocation.tryParse(projectId);
        if (id == null) {
            return 0L;
        }
        return ProgressionStats.count(completions, id);
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
        // project_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "project_id", "ftbquests.task.mcaquests.project_completed.project_id",
                projectId, v -> projectId = v, "", ClientKnownIds.projectIds());
    }

    @Override
    public MutableComponent getAltTitle() {
        if (!projectId.isEmpty()) {
            return Component.translatable("ftbquests.task.mcaquests.project_completed.alt_title.specific", projectId, count);
        }
        return Component.translatable("ftbquests.task.mcaquests.project_completed.alt_title.any", count);
    }
}
