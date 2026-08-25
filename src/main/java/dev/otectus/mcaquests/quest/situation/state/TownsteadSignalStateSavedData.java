package dev.otectus.mcaquests.quest.situation.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Townstead state looked like the last time it was observed, so a signal fires on a
 * <em>change</em> rather than on every scan (Townstead spec §7.3).
 *
 * <h2>Why this has to be persisted</h2>
 *
 * <p>Without it, every restart would look like the whole village had just changed at once: every
 * collapsed villager would collapse again, every master artisan would be promoted again, and every
 * player would be greeted by a queue of situations for things that happened days ago. Remembering the
 * previous reading is the only thing that separates "this is news" from "this is Tuesday".
 *
 * <p>A first observation is deliberately <b>never</b> news. The first time a village is seen there is
 * nothing to compare against, so the reading is recorded and nothing fires — otherwise installing this
 * mod on an existing world would open a situation for every villager in it.
 *
 * <h2>Why this one is versioned when nothing else in the mod is</h2>
 *
 * <p>Every other store here evolves by adding optional tags guarded by {@code contains}, which works
 * because their contents are independent facts. This one is a <em>comparison baseline</em>: if the
 * meaning of a stored number ever changes, silently reading the old one produces a wrong answer rather
 * than a missing one, and wrong answers here look like spurious situations rather than like a bug. A
 * version lets a future change say "these readings mean something else now, take them again" instead.
 */
public final class TownsteadSignalStateSavedData extends SavedData {

    public static final String DATA_NAME = "mcaquests_townstead_signals";

    /** Bumped only when a stored reading changes meaning; see the class javadoc. */
    private static final int SCHEMA = 1;

    private static final String K_SCHEMA = "schema";
    private static final String K_READINGS = "readings";

    /**
     * Key to last observed value. Keys are composed by the detector and are deliberately plain strings
     * ({@code "12|need|hunger"}, {@code "12|spirit"}, {@code "<uuid>|collapsed"}) so nothing
     * Townstead-shaped is persisted — only numbers whose meaning this mod owns.
     */
    private final Map<String, Integer> readings = new LinkedHashMap<>();

    public TownsteadSignalStateSavedData() {
    }

    public static TownsteadSignalStateSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TownsteadSignalStateSavedData::load, TownsteadSignalStateSavedData::new, DATA_NAME);
    }

    /**
     * Records an observation and reports what changed.
     *
     * @return {@code true} only when there was a previous reading <em>and</em> it differed, so a first
     *         sighting is recorded silently
     */
    public boolean observeChanged(String key, int value) {
        Integer previous = readings.put(key, value);
        if (previous == null) {
            setDirty();
            return false; // first sighting: remember it, announce nothing
        }
        if (previous == value) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * As {@link #observeChanged}, but only an <em>increase</em> is news — for tiers, which are worth
     * celebrating when they rise and not worth mentioning when a building is lost.
     */
    public boolean observeIncrease(String key, int value) {
        Integer previous = readings.put(key, value);
        if (previous == null) {
            setDirty();
            return false;
        }
        if (value <= previous) {
            if (value != previous) {
                setDirty(); // a fall is still recorded, so the next rise is measured from here
            }
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * As {@link #observeChanged}, but only a false-to-true crossing is news — for states like collapse,
     * which should be announced once rather than every second the villager stays down.
     */
    public boolean observeRisingEdge(String key, boolean value) {
        return observeIncrease(key, value ? 1 : 0);
    }

    /** The previous reading, or {@code fallback} when this key has never been seen. */
    public int lastReading(String key, int fallback) {
        return readings.getOrDefault(key, fallback);
    }

    /** Forgets a key, for a village or villager that no longer exists. */
    public void forget(String key) {
        if (readings.remove(key) != null) {
            setDirty();
        }
    }

    public int size() {
        return readings.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(K_SCHEMA, SCHEMA);
        CompoundTag stored = new CompoundTag();
        readings.forEach(stored::putInt);
        tag.put(K_READINGS, stored);
        return tag;
    }

    public static TownsteadSignalStateSavedData load(CompoundTag tag) {
        TownsteadSignalStateSavedData data = new TownsteadSignalStateSavedData();
        int schema = tag.contains(K_SCHEMA, Tag.TAG_INT) ? tag.getInt(K_SCHEMA) : 0;
        if (schema != SCHEMA) {
            // Deterministic migration: discard. These are only comparison baselines, so the cost of
            // dropping them is that the next scan re-observes and stays quiet -- which is exactly the
            // first-sighting behaviour, and strictly better than comparing against a number that no
            // longer means what it did.
            McaQuests.LOGGER.info("[MCA: Quests] Townstead signal baselines were written by schema {} "
                    + "and this build uses {}; they will be taken again on the next scan. No situations "
                    + "are lost.", schema, SCHEMA);
            return data;
        }
        CompoundTag stored = tag.getCompound(K_READINGS);
        for (String key : stored.getAllKeys()) {
            data.readings.put(key, stored.getInt(key));
        }
        return data;
    }
}
