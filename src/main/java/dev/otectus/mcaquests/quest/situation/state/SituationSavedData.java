package dev.otectus.mcaquests.quest.situation.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single world-level store for all open situations (the "Living Village" phase, 0.8.0). Pinned to
 * the overworld's {@link net.minecraft.world.level.storage.DimensionDataStorage} so one store holds
 * every village's situations regardless of where players are, exactly like {@code ProjectSavedData}.
 * Persists to {@code <world>/data/mcaquests_situations.dat} on autosave/shutdown. {@link #setDirty()} is
 * called after every mutation.
 *
 * <p>Alongside the live instances it keeps a per-(village, definition) cooldown table: the game time
 * before which that definition may not re-open in that village (anti-spam throttle bookkeeping).
 */
public final class SituationSavedData extends SavedData {

    public static final String DATA_NAME = "mcaquests_situations";

    private final Map<UUID, SituationInstance> instances = new LinkedHashMap<>();
    /** Key {@code "<villageId>|<defId>"} -> earliest game time the definition may re-open in the village. */
    private final Map<String, Long> cooldownUntil = new LinkedHashMap<>();
    /** Village id -> earliest game time <em>any</em> situation may open there (global anti-spam). */
    private final Map<Integer, Long> globalCooldownUntil = new LinkedHashMap<>();

    public SituationSavedData() {
    }

    public static SituationSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        // DATA_NAME is unchanged from 1.20.1 — the .dat file is loader-independent, so existing
        // worlds keep their situations through the upgrade. DataFixTypes null: no vanilla DFU applies.
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SituationSavedData::new,
                        (tag, provider) -> SituationSavedData.load(tag),
                        null),
                DATA_NAME);
    }

    // --- instances ---

    public Optional<SituationInstance> getInstance(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public void putInstance(SituationInstance instance) {
        instances.put(instance.instanceId(), instance);
        setDirty();
    }

    public void removeInstance(UUID instanceId) {
        if (instances.remove(instanceId) != null) {
            setDirty();
        }
    }

    /** Records {@code player} as a participant in an open instance (0.8.0); no-op if it is gone. */
    public void recordParticipant(UUID instanceId, UUID player) {
        SituationInstance instance = instances.get(instanceId);
        if (instance != null && instance.addParticipant(player)) {
            setDirty();
        }
    }

    public Collection<SituationInstance> allInstances() {
        return new ArrayList<>(instances.values());
    }

    /** Open instances scoped to {@code villageId}. */
    public List<SituationInstance> openInstancesInVillage(int villageId) {
        List<SituationInstance> out = new ArrayList<>();
        for (SituationInstance instance : instances.values()) {
            if (instance.isOpen() && instance.villageId() == villageId) {
                out.add(instance);
            }
        }
        return out;
    }

    /** Count of open instances scoped to {@code villageId} (used by the concurrency throttle). */
    public int openCountInVillage(int villageId) {
        return openInstancesInVillage(villageId).size();
    }

    /** Whether an open instance of {@code defId} already exists in {@code villageId} (dedupe guard). */
    public boolean hasOpenOfDef(int villageId, ResourceLocation defId) {
        for (SituationInstance instance : instances.values()) {
            if (instance.isOpen() && instance.villageId() == villageId && instance.defId().equals(defId)) {
                return true;
            }
        }
        return false;
    }

    // --- cooldowns ---

    private static String cooldownKey(int villageId, ResourceLocation defId) {
        return villageId + "|" + defId;
    }

    public long cooldownUntil(int villageId, ResourceLocation defId) {
        return cooldownUntil.getOrDefault(cooldownKey(villageId, defId), Long.MIN_VALUE);
    }

    public void setCooldownUntil(int villageId, ResourceLocation defId, long gameTime) {
        cooldownUntil.put(cooldownKey(villageId, defId), gameTime);
        setDirty();
    }

    public long globalCooldownUntil(int villageId) {
        return globalCooldownUntil.getOrDefault(villageId, Long.MIN_VALUE);
    }

    public void setGlobalCooldownUntil(int villageId, long gameTime) {
        globalCooldownUntil.put(villageId, gameTime);
        setDirty();
    }

    // --- persistence ---

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag instanceList = new ListTag();
        for (SituationInstance instance : instances.values()) {
            instanceList.add(instance.save());
        }
        tag.put("instances", instanceList);

        CompoundTag cooldowns = new CompoundTag();
        cooldownUntil.forEach(cooldowns::putLong);
        tag.put("cooldowns", cooldowns);

        CompoundTag globalCooldowns = new CompoundTag();
        globalCooldownUntil.forEach((villageId, until) -> globalCooldowns.putLong(Integer.toString(villageId), until));
        tag.put("global_cooldowns", globalCooldowns);
        return tag;
    }

    public static SituationSavedData load(CompoundTag tag) {
        SituationSavedData data = new SituationSavedData();
        ListTag instanceList = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instanceList.size(); i++) {
            SituationInstance instance = SituationInstance.load(instanceList.getCompound(i));
            data.instances.put(instance.instanceId(), instance);
        }
        CompoundTag cooldowns = tag.getCompound("cooldowns");
        for (String key : cooldowns.getAllKeys()) {
            data.cooldownUntil.put(key, cooldowns.getLong(key));
        }
        CompoundTag globalCooldowns = tag.getCompound("global_cooldowns");
        for (String key : globalCooldowns.getAllKeys()) {
            try {
                data.globalCooldownUntil.put(Integer.parseInt(key), globalCooldowns.getLong(key));
            } catch (NumberFormatException ignored) {
                // skip malformed village-id key
            }
        }
        return data;
    }
}
