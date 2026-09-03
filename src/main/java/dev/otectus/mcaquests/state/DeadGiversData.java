package dev.otectus.mcaquests.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quest givers known to have died, so a player who was offline at the time is reconciled when they
 * next log in.
 *
 * <h2>Why a record rather than a live check</h2>
 *
 * <p>{@code QuestProgressEvents.onGiverDeath} only ever saw the players who were online: anyone else
 * kept an active quest pointing at a villager who no longer exists, and {@code fail_on_giver_death}
 * simply never fired for them — in a chain, the failure branch never opened.
 *
 * <p>Login cannot re-derive that by itself. {@code ServerLevel#getEntity} answers "not loaded" and
 * "does not exist" with the same empty, and a village three thousand blocks away is unloaded almost
 * all of the time; failing every quest whose giver is not in memory would destroy quests over nothing.
 * A death, by contrast, is an event, and this is where it is written down.
 *
 * <p>Entries are pruned by age ({@link #RETENTION_TICKS}, twenty in-game days) rather than per player:
 * one death may concern several offline players, and a player who has already been reconciled no
 * longer holds a quest with that giver, so re-reading the entry does nothing. Pinned to the overworld's
 * data storage, matching {@link PendingHeartsData}.
 */
public final class DeadGiversData extends SavedData {

    public static final String DATA_NAME = "mcaquests_dead_givers";

    /** DataFixTypes is null: no vanilla data fixer applies to this mod's own store. */
    public static final SavedData.Factory<DeadGiversData> FACTORY =
            new SavedData.Factory<>(DeadGiversData::new, DeadGiversData::load, null);

    /** How long a death stays on the books: long enough for a returning player, short enough to bound. */
    public static final long RETENTION_TICKS = 20L * 24000L;

    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_GIVER = "giver";
    private static final String KEY_TICK = "tick";

    private final Map<UUID, Long> deaths = new HashMap<>();

    public DeadGiversData() {
    }

    public static DeadGiversData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Records that {@code giver} died at {@code gameTime}, and drops anything older than the retention. */
    public void record(UUID giver, long gameTime) {
        if (giver == null) {
            return;
        }
        deaths.put(giver, gameTime);
        deaths.values().removeIf(when -> gameTime - when > RETENTION_TICKS);
        setDirty();
    }

    /** True when this villager is known to have died (rather than merely being out of loaded chunks). */
    public boolean isDead(UUID giver) {
        return deaths.containsKey(giver);
    }

    /** True when nothing at all is recorded — lets the login handler skip the whole pass. */
    public boolean isEmpty() {
        return deaths.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        deaths.forEach((giver, when) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_GIVER, giver);
            entry.putLong(KEY_TICK, when);
            entries.add(entry);
        });
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static DeadGiversData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadGiversData data = new DeadGiversData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID(KEY_GIVER)) {
                data.deaths.put(entry.getUUID(KEY_GIVER), entry.getLong(KEY_TICK));
            }
        }
        return data;
    }
}
