package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.JournalArchiveEntry;
import dev.otectus.mcaquests.network.JournalSyncS2CPacket;
import dev.otectus.mcaquests.network.JournalVillageEntry;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.quest.title.Titles;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds the server-authoritative journal snapshot (spec 0.7.0): per-village reputation/tier/titles, the
 * player's global titles, and a completed-quest archive. The archive builder is a pure static method so
 * it can be unit-tested without game registries.
 */
public final class JournalService {

    /** Cap on archive lines sent to the client, so a long history cannot bloat the packet. */
    public static final int ARCHIVE_CAP = 100;

    private JournalService() {
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ProjectSavedData saved = ProjectSavedData.get(level.getServer());
        ReputationTierSet ladder = ReputationTiers.getDefault();

        List<Component> globalTitles = new ArrayList<>();
        data.titles().global().forEach(id -> globalTitles.add(Titles.displayName(id)));

        List<JournalVillageEntry> villages = new ArrayList<>();
        for (int villageId : knownVillages(saved, data)) {
            villages.add(villageEntry(level, saved, data, ladder, villageId));
        }

        Function<ResourceLocation, Component> titleResolver = id ->
                QuestRegistry.get(id).map(QuestDefinition::title).orElseGet(() -> Component.literal(id.toString()));
        List<JournalArchiveEntry> archive = buildArchive(data.history().completionsView(), titleResolver, ARCHIVE_CAP);

        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new JournalSyncS2CPacket(globalTitles, villages, archive));
    }

    /** Villages the player "knows": any with a reputation value, plus any they hold a title with. */
    private static Set<Integer> knownVillages(ProjectSavedData saved, PlayerQuestData data) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String key : saved.reputationKeys()) {
            ReputationService.parseVillageId(key).ifPresent(ids::add);
        }
        ids.addAll(data.titles().byVillage().keySet());
        return ids;
    }

    private static JournalVillageEntry villageEntry(ServerLevel level, ProjectSavedData saved, PlayerQuestData data,
                                                    ReputationTierSet ladder, int villageId) {
        Component name = McaCompat.villageName(level, villageId)
                .map(s -> (Component) Component.literal(s))
                .orElseGet(() -> Component.translatable("mcaquests.journal.village_fallback", villageId));
        int rep = saved.reputation("v:" + villageId);
        Component currentTier = Component.literal(ladder.tierFor(rep).name());
        ReputationTier next = ladder.nextTier(rep).orElse(null);
        Component nextTier = next != null ? Component.literal(next.name()) : Component.empty();
        int nextThreshold = next != null ? next.threshold() : -1;

        List<Component> titles = new ArrayList<>();
        data.titles().forVillage(villageId).forEach(id -> titles.add(Titles.displayName(id)));

        return new JournalVillageEntry(name, rep, currentTier, nextTier, nextThreshold, titles);
    }

    /**
     * Pure: turns a quest-completion map into sorted, capped archive lines (most-completed first, then by
     * id for stability). {@code titleResolver} maps a quest id to its display title.
     */
    public static List<JournalArchiveEntry> buildArchive(Map<ResourceLocation, Integer> completions,
                                                         Function<ResourceLocation, Component> titleResolver,
                                                         int cap) {
        return completions.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<ResourceLocation, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .limit(cap)
                .map(e -> new JournalArchiveEntry(titleResolver.apply(e.getKey()), e.getValue()))
                .toList();
    }
}
