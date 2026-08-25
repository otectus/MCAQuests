package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hearts owed to a villager who was not loaded when the reward was granted, kept until the villager
 * and the player are both available.
 *
 * <h2>Why MCA: Quests owns this</h2>
 *
 * <p>Community-project and situation rewards pay hearts to every resident of a village, but a resident
 * who is unloaded cannot receive them there and then. Through MCA 7.6 this mod handed the amount to
 * MCA's own {@code Village#pushHearts(UUID,int)} queue — but MCA deleted that queue (and the whole
 * "unspent hearts" mechanism behind it) in the 7.7 line, so there is nothing left to hand off to.
 *
 * <p>Keeping the ledger here is also more faithful than the old behaviour was. MCA's queue was
 * village-wide and player-agnostic, while the loaded-villager path right beside it
 * ({@code McaCompat.addHearts}) has always been <em>per player</em> — the same grant therefore meant
 * two different things depending on whether a chunk happened to be loaded. This ledger records the
 * player, so loaded and unloaded residents now receive exactly the same reward, on every MCA version.
 *
 * <p>Stored as {@code villager UUID -> player UUID -> amount} and pinned to the overworld's data
 * storage, matching {@code ProjectSavedData}. Amounts for the same pair accumulate, and an entry that
 * sums to zero is dropped rather than persisted. Entries survive restarts; they are drained by
 * {@code PendingHeartsEvents} when the villager next loads or the player next logs in.
 */
public final class PendingHeartsData extends SavedData {

    public static final String DATA_NAME = "mcaquests_pending_hearts";

    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_VILLAGER = "villager";
    private static final String KEY_OWED = "owed";
    private static final String KEY_PLAYER = "player";
    private static final String KEY_AMOUNT = "amount";

    private final Map<UUID, Map<UUID, Integer>> owed = new LinkedHashMap<>();

    public PendingHeartsData() {
    }

    public static PendingHeartsData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(PendingHeartsData::load, PendingHeartsData::new, DATA_NAME);
    }

    /**
     * Adds {@code amount} hearts owed by {@code player} to {@code villager}. Amounts accumulate, and a
     * pair whose running total reaches zero is removed — so equal and opposite grants leave no trace.
     */
    public void queue(UUID villager, UUID player, int amount) {
        if (villager == null || player == null || amount == 0) {
            return;
        }
        Map<UUID, Integer> perPlayer = owed.computeIfAbsent(villager, key -> new LinkedHashMap<>());
        int total = perPlayer.merge(player, amount, Integer::sum);
        if (total == 0) {
            perPlayer.remove(player);
        }
        if (perPlayer.isEmpty()) {
            owed.remove(villager);
        }
        setDirty();
    }

    /** True when anything at all is owed — lets the join handler skip work in the common case. */
    public boolean isEmpty() {
        return owed.isEmpty();
    }

    /** Everything owed to {@code villager}, as an immutable snapshot. Never null. */
    public Map<UUID, Integer> owedTo(UUID villager) {
        Map<UUID, Integer> perPlayer = owed.get(villager);
        return perPlayer == null ? Map.of() : Map.copyOf(perPlayer);
    }

    /** Every villager with something owed, as a snapshot safe to iterate while draining. */
    public List<UUID> villagersOwed() {
        return new ArrayList<>(owed.keySet());
    }

    /** Clears one (villager, player) debt after it has actually been paid. */
    public void clear(UUID villager, UUID player) {
        Map<UUID, Integer> perPlayer = owed.get(villager);
        if (perPlayer == null || perPlayer.remove(player) == null) {
            return;
        }
        if (perPlayer.isEmpty()) {
            owed.remove(villager);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        owed.forEach((villager, perPlayer) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_VILLAGER, villager);
            ListTag debts = new ListTag();
            perPlayer.forEach((player, amount) -> {
                CompoundTag debt = new CompoundTag();
                debt.putUUID(KEY_PLAYER, player);
                debt.putInt(KEY_AMOUNT, amount);
                debts.add(debt);
            });
            entry.put(KEY_OWED, debts);
            entries.add(entry);
        });
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static PendingHeartsData load(CompoundTag tag) {
        PendingHeartsData data = new PendingHeartsData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(KEY_VILLAGER)) {
                continue;
            }
            UUID villager = entry.getUUID(KEY_VILLAGER);
            Map<UUID, Integer> perPlayer = new LinkedHashMap<>();
            ListTag debts = entry.getList(KEY_OWED, Tag.TAG_COMPOUND);
            for (int j = 0; j < debts.size(); j++) {
                CompoundTag debt = debts.getCompound(j);
                if (!debt.hasUUID(KEY_PLAYER)) {
                    continue;
                }
                int amount = debt.getInt(KEY_AMOUNT);
                if (amount != 0) {
                    perPlayer.put(debt.getUUID(KEY_PLAYER), amount);
                }
            }
            if (!perPlayer.isEmpty()) {
                data.owed.put(villager, perPlayer);
            }
        }
        return data;
    }
}
