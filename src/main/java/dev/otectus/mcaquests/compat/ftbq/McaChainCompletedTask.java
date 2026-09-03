package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * {@code mcaquests:chain_completed} (spec §15.2) — true once the player's history contains a completion
 * of any quest whose {@link ChainSpec#chain()} equals {@code chain_id} <em>and</em> which is a final
 * stage per {@link ChainSpec#isFinalStage()} (branching arcs may have more than one final stage; any
 * match counts).
 *
 * <p>{@code chain_id} is required in practice: an empty id can never match a chain (no quest's
 * {@code ChainSpec.chain()} is empty), so {@link #check} short-circuits to {@code false} rather than
 * scanning history for nothing. Authoring mistakes (empty/typo'd id) surface later via
 * {@code /mcaquests ftbq validate} (§21), not here.
 */
public class McaChainCompletedTask extends McaBooleanTaskBase {

    private String chainId = "";

    public McaChainCompletedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.CHAIN_COMPLETED;
    }

    /** Package-private accessor for {@code FtbqBridgeImpl}'s book→MCA validate sweep (spec §21). */
    String chainId() {
        return chainId;
    }

    @Override
    protected boolean check(ServerPlayer player) {
        if (chainId.isEmpty()) {
            return false;
        }
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        if (data.isEmpty()) {
            return false;
        }
        for (ResourceLocation completedId : data.get().history().completionsView().keySet()) {
            Optional<QuestDefinition> resolved = QuestDefinitions.resolve(completedId);
            if (resolved.isEmpty()) {
                continue;
            }
            Optional<ChainSpec> chain = resolved.get().chain();
            if (chain.isPresent() && chainId.equals(chain.get().chain()) && chain.get().isFinalStage()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("chain_id", chainId);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        chainId = nbt.getString("chain_id");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(chainId, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        chainId = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // chain_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows, with the
        // synced display name (ClientKnownIds.lookupChainName) rather than the raw id in the dropdown.
        IdConfigRows.addIdField(config, "chain_id", "ftbquests.task.mcaquests.chain_completed.chain_id",
                chainId, v -> chainId = v, "", ClientKnownIds.chainIds(), ClientKnownIds::lookupChainName);
    }

    /**
     * {@code "Finish the '<arc name>' story"}. Resolves {@code chainId} through
     * {@link ClientKnownIds#lookupChainName} (task M2.3's deferred debt, closed at M5.2) — a single map
     * lookup plus one {@code Component.translatable} localization, cheap enough for a per-tooltip-frame
     * call, and it falls back to the raw id automatically when {@code ClientKnownIds} hasn't synced yet
     * (empty client cache → {@code lookupChainName} returns its input unchanged) — exactly today's
     * behaviour in that case.
     */
    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.mcaquests.chain_completed.alt_title",
                ClientKnownIds.lookupChainName(chainId));
    }
}
