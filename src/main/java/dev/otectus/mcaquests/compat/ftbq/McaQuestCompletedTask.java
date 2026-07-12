package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * {@code mcaquests:quest_completed} (spec §15.1) — the hardest pilot task: sums the submitting
 * player's {@link QuestHistory#completionsView()} entries whose resolved {@code QuestDefinition}
 * passes the configured {@link QuestFilter}, counting repeat completions, capped at {@code count}
 * (the cap is applied by {@link McaCounterTaskBase#submitTask} via {@code Math.min(value,
 * getMaxProgress())} — this class only needs to return the uncapped sum).
 */
public class McaQuestCompletedTask extends McaCounterTaskBase {

    private String questId = "";
    private String profession = "";
    private String chainId = "";
    private String category = "";

    public McaQuestCompletedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.QUEST_COMPLETED;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        if (data.isEmpty()) {
            return 0L;
        }
        QuestFilter filter = new QuestFilter(questId, profession, chainId, category);
        McaQuestsConfig.ProfessionMatchingMode mode = McaQuestsConfig.COMMON.professionMatchingMode.get();
        Map<ResourceLocation, Integer> completions = data.get().history().completionsView();
        long total = 0L;
        for (Map.Entry<ResourceLocation, Integer> entry : completions.entrySet()) {
            if (filter.matches(entry.getKey(), QuestDefinitions::resolve, mode)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("quest_id", questId);
        nbt.putString("profession", profession);
        nbt.putString("chain_id", chainId);
        nbt.putString("category", category);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        questId = nbt.getString("quest_id");
        profession = nbt.getString("profession");
        chainId = nbt.getString("chain_id");
        category = nbt.getString("category");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(questId, Short.MAX_VALUE);
        buffer.writeUtf(profession, Short.MAX_VALUE);
        buffer.writeUtf(chainId, Short.MAX_VALUE);
        buffer.writeUtf(category, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        questId = buffer.readUtf(Short.MAX_VALUE);
        profession = buffer.readUtf(Short.MAX_VALUE);
        chainId = buffer.readUtf(Short.MAX_VALUE);
        category = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // quest_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "quest_id", "ftbquests.task.mcaquests.quest_completed.quest_id",
                questId, v -> questId = v, "", ClientKnownIds.questIds());
        // profession/chain_id/category are NOT in the synced known-ids payload (profession would need
        // ProfessionMatcher's known professions, which §20 doesn't sync; chain_id/category here scope a
        // *filter*, not an identity match) — plain free-text, per task M5.2's brief.
        config.addString("profession", profession, v -> profession = v, "")
                .setNameKey("ftbquests.task.mcaquests.quest_completed.profession");
        config.addString("chain_id", chainId, v -> chainId = v, "")
                .setNameKey("ftbquests.task.mcaquests.quest_completed.chain_id");
        config.addString("category", category, v -> category = v, "")
                .setNameKey("ftbquests.task.mcaquests.quest_completed.category");
    }

    @Override
    public MutableComponent getAltTitle() {
        if (!questId.isEmpty() && !questId.endsWith(":*")) {
            return Component.translatable("ftbquests.task.mcaquests.quest_completed.alt_title.specific", questId);
        }
        if (!profession.isEmpty()) {
            return Component.translatable("ftbquests.task.mcaquests.quest_completed.alt_title.profession", count, profession);
        }
        return Component.translatable("ftbquests.task.mcaquests.quest_completed.alt_title.any", count);
    }

    /**
     * Shows the team's already-recorded progress — cheap because it is the cached, monotone
     * {@code teamData.getProgress(this)} value, not a live recompute (this callback has no
     * {@code ServerPlayer} to recompute {@link #currentValue} against in the first place).
     */
    @Override
    public void addMouseOverText(TooltipList list, TeamData teamData) {
        super.addMouseOverText(list, teamData);
        list.add(Component.translatable("ftbquests.task.mcaquests.quest_completed.progress",
                teamData.getProgress(this), count));
    }
}
