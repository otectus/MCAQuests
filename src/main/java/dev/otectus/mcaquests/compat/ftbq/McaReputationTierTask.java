package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code mcaquests:reputation_tier} (spec §15.4) — true once at least {@code village_count} villages
 * have reached tier {@code tier} (or higher) on ladder {@code ladder}. Resolves the required tier's
 * index once per check via {@link ReputationTierSet#indexOf(String)} (§11.4's {@code tierIndex} idiom)
 * and compares every village's {@link ReputationService#currentTier} index against it.
 *
 * <p>An unknown ladder or tier id fails safe to {@code false} (never met) and logs one WARN — not one
 * per check (that would spam the log every poll interval), but one per distinct (ladder, tier) pair,
 * until {@link #clearWarned()} runs. {@link FtbqEventBridge} calls {@link #clearWarned()} on every
 * {@code ClearFileCacheEvent} and on server stopping (§12.3/§12.6), so a datapack fix to a ladder/tier
 * re-arms the warning on the very next reload rather than requiring a JVM restart.
 */
public class McaReputationTierTask extends McaBooleanTaskBase {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private String ladder = "mcaquests:default";
    private String tier = "";
    private int villageCount = 1;

    public McaReputationTierTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.REPUTATION_TIER;
    }

    @Override
    protected boolean check(ServerPlayer player) {
        ResourceLocation ladderId = ResourceLocation.tryParse(ladder);
        Optional<ReputationTierSet> set = ladderId == null ? Optional.empty() : ReputationTiers.get(ladderId);
        if (set.isEmpty()) {
            warnOnce("ladder '" + ladder + "' is not loaded");
            return false;
        }
        int requiredIndex = set.get().indexOf(tier);
        if (requiredIndex < 0) {
            warnOnce("tier '" + tier + "' not found on ladder '" + ladder + "'");
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Map<Integer, Integer> reputations = ReputationService.allVillageReputations(server);
        int qualifying = 0;
        for (int villageId : reputations.keySet()) {
            Optional<ReputationTier> current = ReputationService.currentTier(server, villageId, ladderId);
            if (current.isPresent() && set.get().indexOf(current.get().id()) >= requiredIndex) {
                qualifying++;
            }
        }
        return qualifying >= villageCount;
    }

    private void warnOnce(String reason) {
        if (WARNED.add(ladder + "|" + tier)) {
            McaQuests.LOGGER.warn("[MCA: Quests] FTBQ task {} misconfigured: {}", getType().getTypeId(), reason);
        }
    }

    /** Called by {@link FtbqEventBridge} on cache drop (§12.3) so fixed ladders/tiers re-arm the WARN. */
    static void clearWarned() {
        WARNED.clear();
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("ladder", ladder);
        nbt.putString("tier", tier);
        nbt.putInt("village_count", villageCount);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        ladder = nbt.contains("ladder") ? nbt.getString("ladder") : "mcaquests:default";
        tier = nbt.getString("tier");
        villageCount = Math.max(1, nbt.getInt("village_count"));
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(ladder, Short.MAX_VALUE);
        buffer.writeUtf(tier, Short.MAX_VALUE);
        buffer.writeVarInt(villageCount);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        ladder = buffer.readUtf(Short.MAX_VALUE);
        tier = buffer.readUtf(Short.MAX_VALUE);
        villageCount = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // Dropdowns from synced known ids (§20) land in a later task; plain free-text for now.
        config.addString("ladder", ladder, v -> ladder = v, "mcaquests:default")
                .setNameKey("ftbquests.task.mcaquests.reputation_tier.ladder");
        config.addString("tier", tier, v -> tier = v, "")
                .setNameKey("ftbquests.task.mcaquests.reputation_tier.tier");
        config.addInt("village_count", villageCount, v -> villageCount = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("ftbquests.task.mcaquests.reputation_tier.village_count");
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.mcaquests.reputation_tier.alt_title", tier, villageCount);
    }
}
