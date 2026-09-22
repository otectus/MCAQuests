package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * When each definition this world has ever loaded was <em>not</em> loaded, in game time (1.7.0).
 *
 * <h2>Why a ledger</h2>
 *
 * <p>A quest, project or situation whose definition is missing — its optional mod removed, its pack
 * unmounted, its file gone — is paused, and its deadline must not run down while it is. Until 1.7.0 that
 * pause was accrued a second at a time by whatever noticed it: an ordinary quest in its owner's online
 * poll, a project in a sweep that only runs while someone is online. A mod removed while the owner, or
 * everybody, was offline therefore went uncredited, and the quest expired the moment its owner came back.
 *
 * <p>Whether a definition is loaded can only change when the server starts or datapacks reload, so it is
 * sampled exactly then ({@link #sample}) and recorded as intervals. A clock then credits the overlap of
 * those intervals with however much time it missed, online or not ({@link #overlap}). Every definition
 * the world has ever loaded is remembered by key, so one that disappears is noticed without the ledger
 * having to know in advance what might disappear. Game time does not pass while the server is down, so an
 * interval left open at shutdown and closed at the next start is exactly the time the server ran without
 * the definition.
 *
 * <p>Bounded: {@link #MAX_INTERVALS} closed intervals per key, the oldest merged into its neighbour.
 */
public final class ContentOutageData extends SavedData {

    public static final String DATA_NAME = "mcaquests_content_outages";

    static final int MAX_INTERVALS = 16;

    private static final String KEY_KNOWN = "known";
    private static final String KEY_OPEN = "open";
    private static final String KEY_CLOSED = "closed";
    private static final String KEY_ID = "key";
    private static final String KEY_SINCE = "since";
    private static final String KEY_SPANS = "spans";

    /** The ledger of the running server; null in tests and between worlds. */
    private static volatile ContentOutageData current;

    private final Set<String> known = new HashSet<>();
    private final Map<String, Long> openSince = new HashMap<>();
    private final Map<String, List<long[]>> closed = new HashMap<>();

    ContentOutageData() {
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads this world's ledger and records what is loaded now. Server start, after datapacks load. */
    public static void attach(MinecraftServer server) {
        current = server.overworld().getDataStorage()
                .computeIfAbsent(ContentOutageData::load, ContentOutageData::new, DATA_NAME);
        sampleNow(server);
    }

    /** Records what is loaded after a datapack reload. */
    public static void sampleNow(MinecraftServer server) {
        ContentOutageData data = current;
        if (data != null) {
            data.sample(server.overworld().getGameTime(), loadedKeys());
        }
    }

    public static void detach() {
        current = null;
    }

    public static Optional<ContentOutageData> current() {
        return Optional.ofNullable(current);
    }

    // ------------------------------------------------------------------ keys

    public static String questKey(ResourceLocation questId) {
        return SituationIds.sourceIdOf(questId).map(ContentOutageData::situationKey).orElse("quest:" + questId);
    }

    public static String projectKey(ResourceLocation projectId) {
        return "project:" + projectId;
    }

    public static String situationKey(ResourceLocation situationId) {
        return "situation:" + situationId;
    }

    private static Set<String> loadedKeys() {
        Set<String> keys = new HashSet<>();
        QuestRegistry.all().forEach(def -> keys.add("quest:" + def.id()));
        SituationRegistry.all().forEach(def -> keys.add(situationKey(def.id())));
        ProjectRegistry.all().forEach(def -> keys.add(projectKey(def.id())));
        return keys;
    }

    // ------------------------------------------------------------------ the ledger

    /** Opens an interval for every known key that is not loaded, and closes one for every key that is. */
    void sample(long now, Set<String> loaded) {
        boolean changed = known.addAll(loaded);
        for (String key : known) {
            Long since = openSince.get(key);
            if (loaded.contains(key)) {
                if (since != null) {
                    openSince.remove(key);
                    if (now > since) {
                        addClosed(key, since, now);
                    }
                    changed = true;
                }
            } else if (since == null) {
                openSince.put(key, now);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private void addClosed(String key, long start, long end) {
        List<long[]> spans = closed.computeIfAbsent(key, k -> new ArrayList<>());
        spans.add(new long[]{start, end});
        while (spans.size() > MAX_INTERVALS) {
            // Merging the two oldest keeps the total covered span a superset, never a loss: an old
            // outage is over-credited slightly rather than forgotten.
            long[] first = spans.remove(0);
            spans.get(0)[0] = Math.min(first[0], spans.get(0)[0]);
        }
    }

    /** Whether {@code key} is being tracked as missing right now. */
    public boolean covers(String key) {
        return openSince.containsKey(key);
    }

    /** Ticks within {@code [from, to)} during which {@code key} was not loaded. */
    public long overlap(String key, long from, long to) {
        if (to <= from) {
            return 0L;
        }
        long total = 0L;
        for (long[] span : closed.getOrDefault(key, List.of())) {
            total += intersect(span[0], span[1], from, to);
        }
        Long since = openSince.get(key);
        if (since != null) {
            total += intersect(since, Long.MAX_VALUE, from, to);
        }
        return total;
    }

    private static long intersect(long start, long end, long from, long to) {
        return Math.max(0L, Math.min(end, to) - Math.max(start, from));
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag knownTag = new ListTag();
        known.stream().sorted().forEach(key -> knownTag.add(StringTag.valueOf(key)));
        tag.put(KEY_KNOWN, knownTag);
        ListTag openTag = new ListTag();
        openSince.forEach((key, since) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ID, key);
            entry.putLong(KEY_SINCE, since);
            openTag.add(entry);
        });
        tag.put(KEY_OPEN, openTag);
        ListTag closedTag = new ListTag();
        closed.forEach((key, spans) -> {
            long[] flat = new long[spans.size() * 2];
            for (int i = 0; i < spans.size(); i++) {
                flat[i * 2] = spans.get(i)[0];
                flat[i * 2 + 1] = spans.get(i)[1];
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ID, key);
            entry.put(KEY_SPANS, new LongArrayTag(flat));
            closedTag.add(entry);
        });
        tag.put(KEY_CLOSED, closedTag);
        return tag;
    }

    public static ContentOutageData load(CompoundTag tag) {
        ContentOutageData data = new ContentOutageData();
        ListTag knownTag = tag.getList(KEY_KNOWN, Tag.TAG_STRING);
        for (int i = 0; i < knownTag.size(); i++) {
            data.known.add(knownTag.getString(i));
        }
        ListTag openTag = tag.getList(KEY_OPEN, Tag.TAG_COMPOUND);
        for (int i = 0; i < openTag.size(); i++) {
            CompoundTag entry = openTag.getCompound(i);
            data.openSince.put(entry.getString(KEY_ID), entry.getLong(KEY_SINCE));
        }
        ListTag closedTag = tag.getList(KEY_CLOSED, Tag.TAG_COMPOUND);
        for (int i = 0; i < closedTag.size(); i++) {
            CompoundTag entry = closedTag.getCompound(i);
            long[] flat = entry.getLongArray(KEY_SPANS);
            List<long[]> spans = new ArrayList<>();
            for (int j = 0; j + 1 < flat.length; j += 2) {
                spans.add(new long[]{flat[j], flat[j + 1]});
            }
            if (!spans.isEmpty()) {
                data.closed.put(entry.getString(KEY_ID), spans);
            }
        }
        return data;
    }
}
