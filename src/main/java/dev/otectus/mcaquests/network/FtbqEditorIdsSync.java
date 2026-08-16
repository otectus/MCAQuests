package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import dev.otectus.mcaquests.quest.title.Titles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and sends {@link FtbqEditorIdsS2CPacket} (spec §20, task M5.1). Called on login
 * ({@code QuestProgressEvents.onPlayerLogin}) and after {@code /mcaquests reload}
 * ({@code McaQuestsCommand.reloadQuests}, right after the existing {@code recheckAll} pass) — the two
 * moments the spec calls out. The packet <em>type</em> is registered unconditionally in
 * {@link QuestNetwork} (§2/§4.3: type registration is never gated); only this send is conditional, on
 * both FTB Quests being loaded server-side and the {@code syncFtbqEditorIds} config. No FTB imports:
 * this only reads mcaquests's own registries.
 */
public final class FtbqEditorIdsSync {

    private FtbqEditorIdsSync() {
    }

    /** Sends the known-ids packet to {@code player} iff FTB Quests is loaded and the sync config is on. */
    public static void maybeSend(ServerPlayer player) {
        if (!shouldSync()) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                buildPacket());
    }

    /**
     * Whether the known-ids packet would actually be sent right now — FTB Quests loaded server-side
     * AND {@code syncFtbqEditorIds} on. Public so {@code /mcaquests ftbq status} (M5.3) can report the
     * same live answer rather than re-deriving it.
     */
    public static boolean shouldSync() {
        return ModList.get().isLoaded("ftbquests") && McaQuestsConfig.COMMON.syncFtbqEditorIds.get();
    }

    /** Snapshots every mcaquests registry into the seven §20 id lists, applying the 64&nbsp;KB budget. */
    static FtbqEditorIdsS2CPacket buildPacket() {
        List<String> questIds = new ArrayList<>();
        Map<String, String> chainNames = new LinkedHashMap<>();
        for (QuestDefinition def : QuestRegistry.all()) {
            questIds.add(def.id().toString());
            def.chain().ifPresent(chain -> recordChainName(chainNames, chain));
        }
        List<String> chainEntries = new ArrayList<>();
        chainNames.forEach((id, name) -> chainEntries.add(id + "|" + name));

        List<String> ladderIds = new ArrayList<>();
        List<String> ladderTierEntries = new ArrayList<>();
        for (ResourceLocation ladderId : ReputationTiers.ids()) {
            ladderIds.add(ladderId.toString());
            ReputationTiers.get(ladderId).ifPresent(set -> addTierEntries(ladderTierEntries, ladderId, set));
        }

        List<String> titleIds = new ArrayList<>();
        for (ResourceLocation id : Titles.ids()) {
            titleIds.add(id.toString());
        }

        List<String> projectIds = new ArrayList<>();
        for (ProjectDefinition def : ProjectRegistry.all()) {
            projectIds.add(def.id().toString());
        }

        List<String> situationIds = new ArrayList<>();
        for (SituationDefinition def : SituationRegistry.all()) {
            situationIds.add(def.id().toString());
        }

        return FtbqEditorIdsS2CPacket.build(questIds, chainEntries, ladderIds, ladderTierEntries,
                titleIds, projectIds, situationIds);
    }

    /**
     * Records {@code chain}'s display name the first time a <em>real</em> one (a {@code relationship_arc}
     * or {@code chapter}) turns up for its chain id — this is the "registry scan for a nicer name" that
     * {@code McaChainCompletedTask.getAltTitle()}'s javadoc deferred at task M2.3. Until then, the raw
     * chain id is held as a placeholder so every chain id is present in the map even if no member ever
     * supplies a real name.
     */
    private static void recordChainName(Map<String, String> chainNames, ChainSpec chain) {
        String chainId = chain.chain();
        String current = chainNames.get(chainId);
        if (current != null && !current.equals(chainId)) {
            return; // a real name was already found for this chain id
        }
        chainNames.put(chainId, realChainDisplayName(chain).orElse(chainId));
    }

    /**
     * The first {@code relationship_arc} or {@code chapter} name on {@code chain}, shipped <em>verbatim</em>:
     * a {@code translate} line contributes its raw translation key, a {@code text} line its literal string.
     * Deliberately NOT resolved server-side — a dedicated server has no client lang files, so resolving here
     * would bake raw keys into the packet with no way to localize. The client consumer
     * ({@code ClientKnownIds.lookupChainName}) runs every name through {@code Component.translatable(...)}
     * uniformly; Minecraft's language lookup returns unmatched keys unchanged, so a literal travels as
     * itself and a key localizes with the client's own lang files.
     */
    private static Optional<String> realChainDisplayName(ChainSpec chain) {
        return chain.relationshipArc().or(chain::chapter)
                .flatMap(text -> text.translate().or(text::text));
    }

    private static void addTierEntries(List<String> out, ResourceLocation ladderId, ReputationTierSet set) {
        for (var tier : set.tiers()) {
            out.add(ladderId + "|" + tier.id());
        }
    }
}
