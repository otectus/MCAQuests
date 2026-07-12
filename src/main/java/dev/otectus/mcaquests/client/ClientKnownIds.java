package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.FtbqEditorIdsS2CPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of the ids last synced by {@link FtbqEditorIdsS2CPacket} (task M5.1) — the ids the
 * FTB Quests editor's {@code mcaquests} condition/reward/task dropdowns can offer. Populated on login
 * and after {@code /mcaquests reload}, while FTB Quests is loaded server-side and
 * {@code syncFtbqEditorIds} is enabled; empty (never {@code null}) otherwise, so callers never need a
 * null check. Zero FTB imports — everything here is plain strings, so this class classloads
 * unconditionally with no {@code NoFtbqClassloadTest} exemption needed.
 *
 * <p>No disconnect hook clears this today — a scan of the client package found no existing
 * "clear client state on logout" precedent to follow (the other {@code Client*Data} holders, e.g.
 * {@code ClientJournalData}/{@code ClientQuestData}, are likewise never explicitly cleared; they're
 * simply overwritten by the next sync). Stale ids surviving a reconnect to a *different* server are
 * therefore possible until the next sync overwrites them — cosmetic only (dropdown suggestions), not
 * used for any validation — but {@link #clear()} is provided so a disconnect hook can be wired up later
 * without touching this class again.
 */
public final class ClientKnownIds {

    private static volatile List<String> questIds = List.of();
    private static volatile List<String> chainIds = List.of();
    private static volatile Map<String, String> chainNames = Map.of();
    private static volatile List<String> ladderIds = List.of();
    private static volatile List<String> ladderTierEntries = List.of();
    private static volatile List<String> titleIds = List.of();
    private static volatile List<String> projectIds = List.of();
    private static volatile List<String> situationIds = List.of();

    private ClientKnownIds() {
    }

    public static void update(FtbqEditorIdsS2CPacket packet) {
        questIds = List.copyOf(packet.questIds());
        Map<String, String> names = splitChainEntries(packet.chainEntries());
        chainIds = List.copyOf(names.keySet());
        chainNames = Map.copyOf(names);
        ladderIds = List.copyOf(packet.ladderIds());
        ladderTierEntries = List.copyOf(packet.ladderTierEntries());
        titleIds = List.copyOf(packet.titleIds());
        projectIds = List.copyOf(packet.projectIds());
        situationIds = List.copyOf(packet.situationIds());
    }

    /** Parses each {@code "<chainId>|<displayName>"} wire entry, splitting on the first {@code '|'}. */
    private static Map<String, String> splitChainEntries(List<String> entries) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String entry : entries) {
            int sep = entry.indexOf('|');
            if (sep >= 0) {
                names.put(entry.substring(0, sep), entry.substring(sep + 1));
            }
        }
        return names;
    }

    /** Resets every list to empty. Not currently hooked to a disconnect event — see the class javadoc. */
    public static void clear() {
        questIds = List.of();
        chainIds = List.of();
        chainNames = Map.of();
        ladderIds = List.of();
        ladderTierEntries = List.of();
        titleIds = List.of();
        projectIds = List.of();
        situationIds = List.of();
    }

    public static List<String> questIds() {
        return questIds;
    }

    /** Distinct chain ids from the last sync (parsed from {@code chainEntries}). */
    public static List<String> chainIds() {
        return chainIds;
    }

    /** The display name for {@code chainId} from the last sync, or {@code chainId} itself if unknown. */
    public static String lookupChainName(String chainId) {
        return chainNames.getOrDefault(chainId, chainId);
    }

    public static List<String> ladderIds() {
        return ladderIds;
    }

    /** {@code "<ladderId>|<tierId>"} flattened entries from the last sync. */
    public static List<String> ladderTierEntries() {
        return ladderTierEntries;
    }

    public static List<String> titleIds() {
        return titleIds;
    }

    public static List<String> projectIds() {
        return projectIds;
    }

    /** Situation *source* definition ids (not per-offer synthetic quest ids) from the last sync. */
    public static List<String> situationIds() {
        return situationIds;
    }
}
