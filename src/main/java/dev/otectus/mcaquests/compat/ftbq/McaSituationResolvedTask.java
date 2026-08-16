package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.ProgressionStats;
import dev.otectus.mcaquests.state.QuestAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * {@code mcaquests:situation_resolved} (spec §15.8) — village Living Village situations resolved
 * <em>successfully</em> (failures/clears don't count — {@link ProgressionStats#situationSuccesses()} is
 * already success-only by construction). {@code situation_id} is the SOURCE definition id (e.g.
 * {@code mcaquests:raiders_at_the_gate}), not the synthetic per-instance offer id.
 */
public class McaSituationResolvedTask extends McaCounterTaskBase {

    private String situationId = "";

    public McaSituationResolvedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.SITUATION_RESOLVED;
    }

    /** Package-private accessor for {@code FtbqBridgeImpl}'s book→MCA validate sweep (spec §21). */
    String situationId() {
        return situationId;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        Optional<PlayerQuestData> data = QuestAttachments.get(player);
        if (data.isEmpty()) {
            return 0L;
        }
        Map<ResourceLocation, Integer> successes = data.get().stats().situationSuccesses();
        if (situationId.isEmpty()) {
            return ProgressionStats.total(successes);
        }
        ResourceLocation id = ResourceLocation.tryParse(situationId);
        if (id == null) {
            return 0L;
        }
        return ProgressionStats.count(successes, id);
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("situation_id", situationId);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        situationId = nbt.getString("situation_id");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(situationId, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        situationId = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // situation_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "situation_id", "ftbquests.task.mcaquests.situation_resolved.situation_id",
                situationId, v -> situationId = v, "", ClientKnownIds.situationIds());
    }

    @Override
    public MutableComponent getAltTitle() {
        if (!situationId.isEmpty()) {
            return Component.translatable("ftbquests.task.mcaquests.situation_resolved.alt_title.specific", situationId, count);
        }
        return Component.translatable("ftbquests.task.mcaquests.situation_resolved.alt_title.any", count);
    }
}
