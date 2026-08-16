package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a snapshot of every id the FTB Quests editor's {@code mcaquests} condition/
 * reward/task dropdowns can offer (spec §20, task M5.1). Sent on login and after
 * {@code /mcaquests reload} — but only when FTB Quests is loaded server-side and the
 * {@code syncFtbqEditorIds} config is on ({@link FtbqEditorIdsSync}). The packet <em>type</em> is
 * still registered unconditionally in {@link QuestNetwork} like every other packet (§2/§4.3: type
 * registration is never gated); only the send is conditional. Zero FTB imports — everything here is
 * plain id/name strings gathered from mcaquests's own registries.
 *
 * <p>Ships exactly seven flat {@code List<String>} payloads, one per §20 enumerated item:
 * <ol>
 *   <li>{@code questIds} — every loaded {@code QuestDefinition} id.</li>
 *   <li>{@code chainEntries} — {@code "<chainId>|<displayName>"}, one per distinct chain id found in
 *       the quest registry. The display name is the first {@code relationship_arc} or {@code chapter}
 *       found on any member of that chain, falling back to the raw chain id when no member supplies
 *       one — see {@link FtbqEditorIdsSync} for the map construction, which is the "registry scan for
 *       a nicer name" that {@code McaChainCompletedTask.getAltTitle()} deferred at task M2.3. Names
 *       travel <em>verbatim</em> as translate-keys-or-literals (a {@code translate} line ships its raw
 *       key, a {@code text} line its literal string) and are localized client-side:
 *       {@link ClientKnownIds#lookupChainName} runs every name through {@code Component.translatable}
 *       uniformly, which localizes known keys and passes literals/unmatched keys through unchanged.
 *       Joined (not parallel-listed) so the wire format is exactly seven lists, matching §20's
 *       enumeration one-for-one; {@code '|'} is rejected in chain ids at validation time
 *       ({@code QuestChainValidator}), so splitting on the first occurrence (done client-side in
 *       {@link ClientKnownIds}) is unambiguous even if a display name itself happens to contain
 *       one.</li>
 *   <li>{@code ladderIds} — every {@code ReputationTierSet} id.</li>
 *   <li>{@code ladderTierEntries} — {@code "<ladderId>|<tierId>"}, flattened, one entry per tier.</li>
 *   <li>{@code titleIds} — every defined title id.</li>
 *   <li>{@code projectIds} — every loaded {@code ProjectDefinition} id.</li>
 *   <li>{@code situationIds} — every loaded situation's <em>source</em> definition id (not the
 *       per-offer synthetic quest id — {@code SituationRegistry.all()}, not {@code bySyntheticId}).</li>
 * </ol>
 *
 * <p><b>Truncation guard:</b> {@link #build} enforces a ~64&nbsp;KB soft byte budget across all seven
 * lists combined, in the order above (approximating each entry's wire cost as its UTF-8 byte length
 * plus a 2-byte length-prefix allowance). Entries are kept until the budget is exhausted; every entry
 * after that point — including the remainder of the list that tipped it over and every list after —
 * is dropped. This runs server-side at packet-<em>build</em> time (not during {@link #encode}), so the
 * truncation and its one-line WARN (with kept/total counts) land in the server log before the packet
 * ever reaches the network layer. Always build via {@link #build}, never the canonical constructor
 * directly, so the guard can't accidentally be bypassed.
 */
public record FtbqEditorIdsS2CPacket(List<String> questIds, List<String> chainEntries, List<String> ladderIds,
                                     List<String> ladderTierEntries, List<String> titleIds,
                                     List<String> projectIds, List<String> situationIds)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FtbqEditorIdsS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "ftbq_editor_ids"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FtbqEditorIdsS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(FtbqEditorIdsS2CPacket::encode, FtbqEditorIdsS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Soft byte budget for the combined payload (spec §20: "~64 KB truncation guard with WARN"). */
    public static final int BYTE_BUDGET = 64 * 1024;

    /** Per-string wire-cost allowance beyond its UTF-8 byte length (a generous bound on the VarInt length prefix). */
    private static final int PER_ENTRY_OVERHEAD = 2;

    /**
     * Builds the packet, applying the {@link #BYTE_BUDGET} truncation guard across all seven lists in
     * §20 order (the order the parameters appear in). Call this — never the canonical constructor —
     * from server-side send sites so the guard and its WARN always apply.
     */
    public static FtbqEditorIdsS2CPacket build(List<String> questIds, List<String> chainEntries,
                                               List<String> ladderIds, List<String> ladderTierEntries,
                                               List<String> titleIds, List<String> projectIds,
                                               List<String> situationIds) {
        Budget budget = new Budget(BYTE_BUDGET);
        FtbqEditorIdsS2CPacket packet = new FtbqEditorIdsS2CPacket(
                budget.take(questIds), budget.take(chainEntries), budget.take(ladderIds),
                budget.take(ladderTierEntries), budget.take(titleIds), budget.take(projectIds),
                budget.take(situationIds));
        if (budget.truncated()) {
            McaQuests.LOGGER.warn(
                    "FTB editor known-ids sync exceeded the {}-byte soft budget; kept {} of {} total id(s) "
                            + "across quest/chain/ladder/tier/title/project/situation lists — the FTB Quests "
                            + "editor dropdowns may be incomplete until this shrinks below budget.",
                    budget.originalBudget, budget.kept, budget.total);
        }
        return packet;
    }

    /** Tracks the shrinking byte budget across successive {@link #take} calls, in §20 list order. */
    private static final class Budget {
        private final int originalBudget;
        private int remaining;
        private int kept;
        private int total;

        Budget(int budget) {
            this.originalBudget = budget;
            this.remaining = budget;
        }

        List<String> take(List<String> entries) {
            List<String> out = new ArrayList<>(entries.size());
            for (String entry : entries) {
                total++;
                int cost = entry.getBytes(StandardCharsets.UTF_8).length + PER_ENTRY_OVERHEAD;
                if (remaining < cost) {
                    continue; // budget exhausted: skip this entry and (since remaining only shrinks) every later one
                }
                remaining -= cost;
                out.add(entry);
                kept++;
            }
            return List.copyOf(out);
        }

        boolean truncated() {
            return kept < total;
        }
    }

    public static void encode(FtbqEditorIdsS2CPacket msg, FriendlyByteBuf buf) {
        writeList(buf, msg.questIds);
        writeList(buf, msg.chainEntries);
        writeList(buf, msg.ladderIds);
        writeList(buf, msg.ladderTierEntries);
        writeList(buf, msg.titleIds);
        writeList(buf, msg.projectIds);
        writeList(buf, msg.situationIds);
    }

    public static FtbqEditorIdsS2CPacket decode(FriendlyByteBuf buf) {
        return new FtbqEditorIdsS2CPacket(readList(buf), readList(buf), readList(buf), readList(buf),
                readList(buf), readList(buf), readList(buf));
    }

    private static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeCollection(list, (b, s) -> b.writeUtf(s, Short.MAX_VALUE));
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        return buf.readCollection(ArrayList::new, b -> b.readUtf(Short.MAX_VALUE));
    }
}
