package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quests' own village-standing store, version 2 (spec §29.2, §32.3).
 *
 * <h2>What changed and why</h2>
 *
 * <p>Version 1 lived in {@code ProjectSavedData.reputation} as {@code "v:<villageId>" → int}. Two
 * defects came with that shape and both are fixed here:
 *
 * <ul>
 *   <li><b>It was shared by the whole world.</b> Every player read the same number for a village even
 *       though the Journal calls it "your standing". On a multiplayer server one player's quest work
 *       silently moved everyone's reputation. v2 keys by player UUID first.</li>
 *   <li><b>It ignored dimensions.</b> MCA allocates village ids per level, so village 3 in the Nether
 *       and village 3 in the overworld shared one entry. v2 keys by {@code dimension/villageId}.</li>
 * </ul>
 *
 * <p>The v1 tags are <b>not</b> deleted. §32.3 requires them retained read-only for rollback and
 * manual recovery, and the migration in {@link #importLegacy} reads them exactly once per player.
 *
 * <p>This store has two jobs. On a Quests-only installation it <em>is</em> the reputation system. With
 * MCA: Reputation installed it is a mirror: canonical commits write through to it so that removing
 * Reputation later leaves Quests with sensible standing rather than resetting everybody to zero
 * (§32.6). Mirror writes never fire events or notifications.
 */
public final class VillageStanding {

    /** Bounded so a long-lived world cannot grow this without limit. */
    private static final int MAX_COMMUNITIES_PER_PLAYER = 256;
    private static final int MAX_TITLES_PER_COMMUNITY = 64;

    /** Per player: community key → score. */
    private final Map<UUID, Map<String, Integer>> scores = new LinkedHashMap<>();
    /** Per player: "ladder|community" → highest tier id ever reached. */
    private final Map<UUID, Map<String, String>> highWater = new LinkedHashMap<>();
    /** Per player: community key → village-scoped titles. */
    private final Map<UUID, Map<String, Set<ResourceLocation>>> titles = new LinkedHashMap<>();
    /** Per player: source id → version, so a legacy import can never run twice. */
    private final Map<UUID, Map<String, String>> migrations = new LinkedHashMap<>();

    /** The canonical string form of a community, matching MCA: Reputation's own. */
    public static String communityKey(ResourceLocation dimension, int villageId) {
        return dimension + "/" + villageId;
    }

    private static String highWaterKey(ResourceLocation ladder, String community) {
        return ladder + "|" + community;
    }

    // ------------------------------------------------------------------
    // Scores
    // ------------------------------------------------------------------

    public int score(UUID player, ResourceLocation dimension, int villageId) {
        return scores.getOrDefault(player, Map.of())
                .getOrDefault(communityKey(dimension, villageId), 0);
    }

    /** @return the resulting score */
    public int addScore(UUID player, ResourceLocation dimension, int villageId, int delta) {
        if (delta == 0) {
            return score(player, dimension, villageId);
        }
        Map<String, Integer> byCommunity = scores.computeIfAbsent(player, id -> new LinkedHashMap<>());
        String key = communityKey(dimension, villageId);
        if (!byCommunity.containsKey(key) && byCommunity.size() >= MAX_COMMUNITIES_PER_PLAYER) {
            return 0;
        }
        // long intermediate: a very long-lived world could otherwise overflow int on the sum
        long updated = (long) byCommunity.getOrDefault(key, 0) + delta;
        int clamped = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, updated));
        byCommunity.put(key, clamped);
        return clamped;
    }

    /** Overwrites a score outright. Used by the mirror, which copies canonical state verbatim. */
    public void setScore(UUID player, ResourceLocation dimension, int villageId, int value) {
        Map<String, Integer> byCommunity = scores.computeIfAbsent(player, id -> new LinkedHashMap<>());
        String key = communityKey(dimension, villageId);
        if (!byCommunity.containsKey(key) && byCommunity.size() >= MAX_COMMUNITIES_PER_PLAYER) {
            return;
        }
        byCommunity.put(key, value);
    }

    /** {@code villageId → score} for one dimension. Backs the FTB "any village" tasks. */
    public Map<Integer, Integer> villageScores(UUID player, ResourceLocation dimension) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        String prefix = dimension + "/";
        scores.getOrDefault(player, Map.of()).forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                try {
                    out.put(Integer.parseInt(key.substring(prefix.length())), value);
                } catch (NumberFormatException ignored) {
                    // a malformed key is skipped rather than failing the whole query
                }
            }
        });
        return out;
    }

    /** Every community key this player has any standing with, for the Journal's village list. */
    public Set<String> communities(UUID player) {
        Set<String> keys = new LinkedHashSet<>(scores.getOrDefault(player, Map.of()).keySet());
        keys.addAll(titles.getOrDefault(player, Map.of()).keySet());
        return keys;
    }

    // ------------------------------------------------------------------
    // Tier high-water
    // ------------------------------------------------------------------

    public Optional<String> tierHighWater(UUID player, ResourceLocation ladder, ResourceLocation dimension,
                                          int villageId) {
        return Optional.ofNullable(highWater.getOrDefault(player, Map.of())
                .get(highWaterKey(ladder, communityKey(dimension, villageId))));
    }

    public void setTierHighWater(UUID player, ResourceLocation ladder, ResourceLocation dimension,
                                 int villageId, String tierId) {
        if (tierId == null || tierId.isBlank()) {
            return;
        }
        highWater.computeIfAbsent(player, id -> new LinkedHashMap<>())
                .put(highWaterKey(ladder, communityKey(dimension, villageId)), tierId);
    }

    // ------------------------------------------------------------------
    // Titles
    // ------------------------------------------------------------------

    public Set<ResourceLocation> villageTitles(UUID player, ResourceLocation dimension, int villageId) {
        return Collections.unmodifiableSet(titles.getOrDefault(player, Map.of())
                .getOrDefault(communityKey(dimension, villageId), Set.of()));
    }

    /** @return true when newly added */
    public boolean grantVillageTitle(UUID player, ResourceLocation dimension, int villageId,
                                     ResourceLocation title) {
        Set<ResourceLocation> held = titles.computeIfAbsent(player, id -> new LinkedHashMap<>())
                .computeIfAbsent(communityKey(dimension, villageId), key -> new LinkedHashSet<>());
        return held.size() < MAX_TITLES_PER_COMMUNITY && held.add(title);
    }

    public boolean hasVillageTitle(UUID player, ResourceLocation dimension, int villageId,
                                   ResourceLocation title) {
        return villageTitles(player, dimension, villageId).contains(title);
    }

    // ------------------------------------------------------------------
    // Migration markers
    // ------------------------------------------------------------------

    public boolean hasMigrated(UUID player, String sourceId) {
        return migrations.getOrDefault(player, Map.of()).containsKey(sourceId);
    }

    public void markMigrated(UUID player, String sourceId, String version) {
        migrations.computeIfAbsent(player, id -> new LinkedHashMap<>()).put(sourceId, version);
    }

    /**
     * Copies the shared v1 values into this player's v2 record, exactly once.
     *
     * <p>The old data cannot be decomposed into individual histories — that information was never
     * recorded — so the honest thing is to hand the player the number they used to see and make
     * everything from here on correct. §32.2 spells out the same policy for MCA: Reputation's import.
     *
     * @param legacyScores   v1 {@code "v:<id>" → score}
     * @param legacyHighWater v1 {@code "v:<id>" → tier id}
     * @param dimension      the dimension the old keys are assumed to have meant — always the overworld
     * @return the number of communities imported
     */
    public int importLegacy(UUID player, Map<String, Integer> legacyScores,
                            Map<String, String> legacyHighWater, ResourceLocation dimension,
                            ResourceLocation ladder) {
        int imported = 0;
        for (Map.Entry<String, Integer> entry : legacyScores.entrySet()) {
            Optional<Integer> villageId = parseLegacyVillageId(entry.getKey());
            if (villageId.isEmpty()) {
                continue;
            }
            addScore(player, dimension, villageId.get(), entry.getValue());
            String tier = legacyHighWater.get(entry.getKey());
            if (tier != null) {
                setTierHighWater(player, ladder, dimension, villageId.get(), tier);
            }
            imported++;
        }
        return imported;
    }

    /** Parses a v1 {@code "v:<id>"} scope identity; empty for any other kind of scope key. */
    public static Optional<Integer> parseLegacyVillageId(String identity) {
        if (identity != null && identity.startsWith("v:")) {
            try {
                return Optional.of(Integer.parseInt(identity.substring(2)));
            } catch (NumberFormatException ignored) {
                // not a village identity
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        CompoundTag players = new CompoundTag();
        Set<UUID> all = new LinkedHashSet<>(scores.keySet());
        all.addAll(highWater.keySet());
        all.addAll(titles.keySet());
        all.addAll(migrations.keySet());

        for (UUID player : all) {
            CompoundTag entry = new CompoundTag();

            CompoundTag scoreTag = new CompoundTag();
            scores.getOrDefault(player, Map.of()).forEach(scoreTag::putInt);
            if (!scoreTag.isEmpty()) {
                entry.put("scores", scoreTag);
            }

            CompoundTag highWaterTag = new CompoundTag();
            highWater.getOrDefault(player, Map.of()).forEach(highWaterTag::putString);
            if (!highWaterTag.isEmpty()) {
                entry.put("highWater", highWaterTag);
            }

            CompoundTag titleTag = new CompoundTag();
            titles.getOrDefault(player, Map.of()).forEach((community, held) -> {
                ListTag list = new ListTag();
                held.forEach(title -> list.add(StringTag.valueOf(title.toString())));
                titleTag.put(community, list);
            });
            if (!titleTag.isEmpty()) {
                entry.put("titles", titleTag);
            }

            CompoundTag migrationTag = new CompoundTag();
            migrations.getOrDefault(player, Map.of()).forEach(migrationTag::putString);
            if (!migrationTag.isEmpty()) {
                entry.put("migrations", migrationTag);
            }

            if (!entry.isEmpty()) {
                players.put(player.toString(), entry);
            }
        }
        root.put("players", players);
        return root;
    }

    /** Per-entry guarded: one unreadable player never costs the others their standing. */
    public static VillageStanding load(CompoundTag root) {
        VillageStanding standing = new VillageStanding();
        if (root == null || root.isEmpty()) {
            return standing;
        }
        CompoundTag players = root.getCompound("players");
        for (String rawUuid : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag entry = players.getCompound(rawUuid);

            CompoundTag scoreTag = entry.getCompound("scores");
            for (String key : scoreTag.getAllKeys()) {
                standing.scores.computeIfAbsent(player, id -> new LinkedHashMap<>())
                        .put(key, scoreTag.getInt(key));
            }

            CompoundTag highWaterTag = entry.getCompound("highWater");
            for (String key : highWaterTag.getAllKeys()) {
                standing.highWater.computeIfAbsent(player, id -> new LinkedHashMap<>())
                        .put(key, highWaterTag.getString(key));
            }

            CompoundTag titleTag = entry.getCompound("titles");
            for (String community : titleTag.getAllKeys()) {
                ListTag list = titleTag.getList(community, Tag.TAG_STRING);
                Set<ResourceLocation> held = new LinkedHashSet<>();
                for (int i = 0; i < list.size() && held.size() < MAX_TITLES_PER_COMMUNITY; i++) {
                    ResourceLocation title = ResourceLocation.tryParse(list.getString(i));
                    if (title != null) {
                        held.add(title);
                    }
                }
                if (!held.isEmpty()) {
                    standing.titles.computeIfAbsent(player, id -> new LinkedHashMap<>())
                            .put(community, held);
                }
            }

            CompoundTag migrationTag = entry.getCompound("migrations");
            for (String key : migrationTag.getAllKeys()) {
                standing.migrations.computeIfAbsent(player, id -> new LinkedHashMap<>())
                        .put(key, migrationTag.getString(key));
            }
        }
        return standing;
    }

    public boolean isEmpty() {
        return scores.isEmpty() && highWater.isEmpty() && titles.isEmpty() && migrations.isEmpty();
    }
}
