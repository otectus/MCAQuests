package dev.otectus.mcaquests.quest.escort;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;

/**
 * Where {@link EscortHoldRegistry}'s leases live between server starts (1.7.0).
 *
 * <p>Until 1.7.0 the registry was memory only, so a restart while a villager was held forgot who held
 * them: abandoning the escort afterwards found nothing to release and the villager stood frozen and
 * invulnerable for good. Each lease names the villager, the player whose escort holds them and whether
 * a release is still owed; the flags the hold overwrote are kept on the villager itself (see
 * {@link EscortHoldRegistry#MARKER}), because they belong to that entity and travel with it. Pinned to
 * the overworld's data storage, like the other world-wide records.
 */
public final class EscortHoldSavedData extends SavedData {

    public static final String DATA_NAME = "mcaquests_escort_holds";

    private static final String KEY_LEASES = "leases";
    private static final String KEY_VILLAGER = "villager";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_PENDING = "pending";

    EscortHoldSavedData() {
    }

    public static EscortHoldSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(EscortHoldSavedData::load, EscortHoldSavedData::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag leases = new ListTag();
        EscortHoldRegistry.snapshot().forEach((villager, lease) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_VILLAGER, villager);
            entry.putUUID(KEY_OWNER, lease.owner());
            if (lease.releasePending()) {
                entry.putBoolean(KEY_PENDING, true);
            }
            leases.add(entry);
        });
        tag.put(KEY_LEASES, leases);
        return tag;
    }

    public static EscortHoldSavedData load(CompoundTag tag) {
        EscortHoldSavedData data = new EscortHoldSavedData();
        ListTag leases = tag.getList(KEY_LEASES, Tag.TAG_COMPOUND);
        Map<UUID, EscortHoldRegistry.Lease> loaded = new java.util.HashMap<>();
        for (int i = 0; i < leases.size(); i++) {
            CompoundTag entry = leases.getCompound(i);
            if (entry.hasUUID(KEY_VILLAGER) && entry.hasUUID(KEY_OWNER)) {
                loaded.put(entry.getUUID(KEY_VILLAGER),
                        new EscortHoldRegistry.Lease(entry.getUUID(KEY_OWNER), entry.getBoolean(KEY_PENDING)));
            }
        }
        EscortHoldRegistry.restore(loaded);
        return data;
    }
}
