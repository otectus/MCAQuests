package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.ClientKnownIds;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** Package-private accessors for {@code FtbqBridgeImpl}'s book→MCA validate sweep (spec §21). */
    String ladder() {
        return ladder;
    }

    String tier() {
        return tier;
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
        Map<Integer, Integer> reputations = dev.otectus.mcaquests.quest.reputation.QuestReputation.overworldVillageScores(server, player.getUUID());
        int qualifying = 0;
        for (Map.Entry<Integer, Integer> entry : reputations.entrySet()) {
            // Resolve the tier from the score we already have rather than asking again: one read
            // per village, and the tier can never disagree with the number it came from.
            ReputationTier current = set.get().tierFor(entry.getValue());
            if (set.get().indexOf(current.id()) >= requiredIndex) {
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
        // ladder is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "ladder", "ftbquests.task.mcaquests.reputation_tier.ladder",
                ladder, v -> ladder = v, "mcaquests:default", ClientKnownIds.ladderIds());
        // tier's dropdown is scoped to whichever ladder is selected *at screen-open time* — FTB
        // Library's ConfigGroup has no live cross-row dependency wiring (each row's contents are fixed
        // when fillConfigGroup runs), so picking a different ladder in the enum row above and expecting
        // the tier dropdown to refresh in the same session isn't possible; re-opening the editor after
        // changing/saving the ladder recomputes this list correctly. Documented per task M5.2's brief.
        IdConfigRows.addIdField(config, "tier", "ftbquests.task.mcaquests.reputation_tier.tier",
                tier, v -> tier = v, "", tiersForCurrentLadder());
        config.addInt("village_count", villageCount, v -> villageCount = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("ftbquests.task.mcaquests.reputation_tier.village_count");
    }

    /**
     * Distinct tier ids belonging to {@link #ladder} (the currently-configured value, read before
     * this call), parsed from {@link ClientKnownIds#ladderTierEntries()}'s flattened
     * {@code "<ladderId>|<tierId>"} entries. Empty when no synced entry matches — including when
     * {@code ladder} itself is a hand-typed id from a not-yet-loaded datapack — which correctly makes
     * {@link IdConfigRows#addIdField} fall back to free-text only for {@code tier} in that case.
     */
    private List<String> tiersForCurrentLadder() {
        Set<String> tiers = new LinkedHashSet<>();
        for (String entry : ClientKnownIds.ladderTierEntries()) {
            int sep = entry.indexOf('|');
            if (sep >= 0 && entry.substring(0, sep).equals(ladder)) {
                tiers.add(entry.substring(sep + 1));
            }
        }
        return new ArrayList<>(tiers);
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.mcaquests.reputation_tier.alt_title", tier, villageCount);
    }
}
