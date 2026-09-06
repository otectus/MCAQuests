package dev.otectus.mcaquests.quest.situation.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What MCA Capitals state looked like the last time it was polled, so a signal fires on a
 * <em>change</em> rather than on every poll (1.6.0).
 *
 * <h2>Why this has to be persisted</h2>
 *
 * <p>A throne stays empty until someone sits on it, and a war lasts until it is settled. Both are
 * states, not moments, so without a remembered baseline every poll would announce the same vacancy and
 * the same war forever, and every restart would announce them again for every capital at once.
 *
 * <p>Persisting it rather than holding it in memory is what makes a war declared thirty seconds before
 * the server stopped still news on the first poll after it comes back: the reading it changed from is
 * on disk. A first observation is deliberately never news -- installing this on a world that already
 * has an empty throne must not open a situation for a death that happened last week.
 *
 * <h2>Why this is a second store rather than the Townstead one</h2>
 *
 * <p>The two detectors share a shape and nothing else. Keeping the baselines apart means a schema
 * change on either side can discard its own readings without taking the other's with it, and a key
 * collision between a village id and a capital id is impossible by construction.
 *
 * <h2>Why this one is versioned when most stores here are not</h2>
 *
 * <p>It is a <em>comparison baseline</em>: if the meaning of a stored value ever changes, silently
 * reading the old one produces a wrong answer rather than a missing one, and wrong answers here look
 * like spurious situations rather than like a bug. A version lets a future change say "these readings
 * mean something else now, take them again" instead.
 */
public final class CapitalsSignalStateSavedData extends SavedData {

    public static final String DATA_NAME = "mcaquests_capitals_signals";

    /** DataFixTypes is null: no vanilla data fixer applies to this mod's own store. */
    public static final SavedData.Factory<CapitalsSignalStateSavedData> FACTORY =
            new SavedData.Factory<>(CapitalsSignalStateSavedData::new, CapitalsSignalStateSavedData::load, null);

    /** Bumped only when a stored reading changes meaning; see the class javadoc. */
    private static final int SCHEMA = 1;

    private static final String K_SCHEMA = "schema";
    private static final String K_READINGS = "readings";
    private static final String K_LABELS = "labels";

    /**
     * Key to last observed value. Keys are composed by the detector and are deliberately plain strings
     * ({@code "<capital-uuid>|interregnum"}) so nothing Capitals-shaped is persisted — only numbers
     * whose meaning this mod owns.
     */
    private final Map<String, Integer> readings = new LinkedHashMap<>();

    /**
     * The same idea for readings that are <em>names</em> rather than numbers: which diplomatic state a
     * pair of capitals was in the last time the pair was looked at.
     *
     * <p>These could have been stored as hash codes in {@link #readings}, and detecting a change would
     * have worked. But a war signal has to report the relation the pair came out of — an
     * alliance collapsing is not a truce lapsing — and a hash cannot be turned back into a state name.
     */
    private final Map<String, String> labels = new LinkedHashMap<>();

    public CapitalsSignalStateSavedData() {
    }

    public static CapitalsSignalStateSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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
     * As {@link #observeChanged}, but only an <em>increase</em> is news — the crossing into a state
     * matters and the crossing back out of it does not.
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
     * As {@link #observeChanged}, but only a false-to-true crossing is news — for states like an empty
     * throne, which should be announced once rather than every poll the throne stays empty.
     */
    public boolean observeRisingEdge(String key, boolean value) {
        return observeIncrease(key, value ? 1 : 0);
    }

    /**
     * Records a named observation and reports the value it replaced.
     *
     * <p>Empty on a first sighting, which callers must treat as "seed, do not fire" — installing this
     * mod on a world would otherwise announce a war that was already being fought. Empty is also
     * returned when nothing changed, so a caller that fires on any present value is correct by
     * construction.
     *
     * <p>An empty {@code value} is not recorded at all: it means the reading could not be taken, and
     * storing it would make the next real reading look like a transition out of nowhere.
     */
    public java.util.Optional<String> observeLabel(String key, String value) {
        if (value == null || value.isEmpty()) {
            return java.util.Optional.empty();
        }
        String previous = labels.put(key, value);
        if (previous == null) {
            setDirty();
            return java.util.Optional.empty(); // first sighting: remember it, announce nothing
        }
        if (previous.equals(value)) {
            return java.util.Optional.empty();
        }
        setDirty();
        return java.util.Optional.of(previous);
    }

    /** The previous named reading, or an empty string when this key has never been seen. */
    public String lastLabel(String key) {
        return labels.getOrDefault(key, "");
    }

    /** The previous reading, or {@code fallback} when this key has never been seen. */
    public int lastReading(String key, int fallback) {
        return readings.getOrDefault(key, fallback);
    }

    /** Forgets a key, for a capital that no longer exists. */
    public void forget(String key) {
        boolean removed = readings.remove(key) != null;
        removed |= labels.remove(key) != null;
        if (removed) {
            setDirty();
        }
    }

    public int size() {
        return readings.size() + labels.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(K_SCHEMA, SCHEMA);
        CompoundTag stored = new CompoundTag();
        readings.forEach(stored::putInt);
        tag.put(K_READINGS, stored);
        if (!labels.isEmpty()) {
            CompoundTag names = new CompoundTag();
            labels.forEach(names::putString);
            tag.put(K_LABELS, names);
        }
        return tag;
    }

    public static CapitalsSignalStateSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CapitalsSignalStateSavedData data = new CapitalsSignalStateSavedData();
        int schema = tag.contains(K_SCHEMA, Tag.TAG_INT) ? tag.getInt(K_SCHEMA) : 0;
        if (schema != SCHEMA) {
            // Deterministic migration: discard. These are only comparison baselines, so the cost of
            // dropping them is that the next scan re-observes and stays quiet -- which is exactly the
            // first-sighting behaviour, and strictly better than comparing against a number that no
            // longer means what it did.
            McaQuests.LOGGER.info("[MCA: Quests] Capitals signal baselines were written by schema {} "
                    + "and this build uses {}; they will be taken again on the next scan. No situations "
                    + "are lost.", schema, SCHEMA);
            return data;
        }
        CompoundTag stored = tag.getCompound(K_READINGS);
        for (String key : stored.getAllKeys()) {
            data.readings.put(key, stored.getInt(key));
        }
        // Absent only on a store written before any pair was seen. An empty label map reads as
        // "never seen", so the first poll seeds the named baselines and stays quiet.
        CompoundTag names = tag.getCompound(K_LABELS);
        for (String key : names.getAllKeys()) {
            data.labels.put(key, names.getString(key));
        }
        return data;
    }
}
