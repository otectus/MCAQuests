package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
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
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("chain_id", chainId);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        chainId = nbt.getString("chain_id");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(chainId, Short.MAX_VALUE);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        chainId = buffer.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // Dropdown-from-synced-ids (§20) lands with the other editor-id work; plain free-text for now,
        // matching McaQuestCompletedTask's precedent (task M2.2).
        config.addString("chain_id", chainId, v -> chainId = v, "")
                .setNameKey("ftbquests.task.mcaquests.chain_completed.chain_id");
    }

    /**
     * {@code "Finish the '<arc name>' story"}. The spec allows the arc display name to fall back to the
     * raw chain id; we always use the raw id here rather than scanning the quest registry for a chain
     * member's {@code relationship_arc}/{@code chapter} text every render — this is called on every
     * tooltip frame, and the id is already a human-authored slug in practice (e.g.
     * {@code the_family_farm}). A registry scan for a nicer name can be added later without touching the
     * wire format.
     */
    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.mcaquests.chain_completed.alt_title", chainId);
    }
}
