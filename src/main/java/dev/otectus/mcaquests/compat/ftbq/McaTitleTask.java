package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.PlayerTitles;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * {@code mcaquests:title} (spec §15.5) — with {@code title_id} set, 1 if the player holds that title
 * anywhere (global or any village), else 0; empty {@code title_id} sums the player's total distinct
 * titles across global + every village, de-duplicated by id (holding the same title id both globally and
 * in a village counts once).
 */
public class McaTitleTask extends McaCounterTaskBase {

    private String titleId = "";

    public McaTitleTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.TITLE;
    }

    /** Package-private accessor for {@code FtbqBridgeImpl}'s book→MCA validate sweep (spec §21). */
    String titleId() {
        return titleId;
    }

    @Override
    protected long currentValue(ServerPlayer player) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        if (data.isEmpty()) {
            return 0L;
        }
        PlayerTitles titles = data.get().titles();
        if (!titleId.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(titleId);
            if (id == null) {
                return 0L;
            }
            if (titles.hasGlobal(id)) {
                return 1L;
            }
            for (Set<ResourceLocation> villageTitles : titles.byVillage().values()) {
                if (villageTitles.contains(id)) {
                    return 1L;
                }
            }
            return 0L;
        }
        Set<ResourceLocation> distinct = new HashSet<>(titles.global());
        for (Set<ResourceLocation> villageTitles : titles.byVillage().values()) {
            distinct.addAll(villageTitles);
        }
        return distinct.size();
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("title_id", titleId);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        titleId = nbt.getString("title_id");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(titleId, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        titleId = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // title_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "title_id", "ftbquests.task.mcaquests.title.title_id",
                titleId, v -> titleId = v, "", ClientKnownIds.titleIds());
    }

    @Override
    public MutableComponent getAltTitle() {
        if (!titleId.isEmpty()) {
            return Component.translatable("ftbquests.task.mcaquests.title.alt_title.specific", titleId);
        }
        return Component.translatable("ftbquests.task.mcaquests.title.alt_title.any", count);
    }
}
