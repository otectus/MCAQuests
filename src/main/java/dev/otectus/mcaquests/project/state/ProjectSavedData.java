package dev.otectus.mcaquests.project.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * The single world-level store for all community projects (spec 0.4.0). Pinned to the overworld's
 * {@link net.minecraft.world.level.storage.DimensionDataStorage} so one store holds every scope
 * regardless of where sponsors currently are. Persists to {@code <world>/data/mcaquests_projects.dat}
 * on autosave/shutdown — the same mechanism MCA's own village/family data uses, so the lifetime matches
 * data the player already trusts. {@link #setDirty()} is called after every mutation.
 */
public final class ProjectSavedData extends SavedData {

    public static final String DATA_NAME = "mcaquests_projects";

    private final Map<String, ProjectState> instances = new LinkedHashMap<>();
    private final Map<String, Integer> reputation = new LinkedHashMap<>();
    private final Map<UUID, List<PendingReward>> pending = new LinkedHashMap<>();

    public ProjectSavedData() {
    }

    public static ProjectSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ProjectSavedData::load, ProjectSavedData::new, DATA_NAME);
    }

    // --- instances ---

    public Optional<ProjectState> getInstance(String key) {
        return Optional.ofNullable(instances.get(key));
    }

    public Optional<ProjectState> getInstance(ProjectInstanceKey key) {
        return getInstance(key.asString());
    }

    public void putInstance(ProjectState state) {
        instances.put(state.key().asString(), state);
        setDirty();
    }

    public void removeInstance(String key) {
        if (instances.remove(key) != null) {
            setDirty();
        }
    }

    public Collection<ProjectState> allInstances() {
        return new ArrayList<>(instances.values());
    }

    // --- reputation (independent mod-side, keyed by scope identity) ---

    public int reputation(String identity) {
        return reputation.getOrDefault(identity, 0);
    }

    public void addReputation(String identity, int delta) {
        if (delta == 0) {
            return;
        }
        reputation.merge(identity, delta, Integer::sum);
        setDirty();
    }

    // --- offline pending player rewards ---

    public void addPending(UUID player, PendingReward reward) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(reward);
        setDirty();
    }

    public List<PendingReward> drainPending(UUID player) {
        List<PendingReward> owed = pending.remove(player);
        if (owed == null || owed.isEmpty()) {
            return List.of();
        }
        setDirty();
        return owed;
    }

    // --- persistence ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag instanceList = new ListTag();
        for (ProjectState state : instances.values()) {
            instanceList.add(state.save());
        }
        tag.put("instances", instanceList);

        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        tag.put("reputation", rep);

        CompoundTag pend = new CompoundTag();
        pending.forEach((uuid, list) -> {
            ListTag rewards = new ListTag();
            list.forEach(r -> rewards.add(r.save()));
            pend.put(uuid.toString(), rewards);
        });
        tag.put("pending", pend);
        return tag;
    }

    public static ProjectSavedData load(CompoundTag tag) {
        ProjectSavedData data = new ProjectSavedData();
        ListTag instanceList = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instanceList.size(); i++) {
            ProjectState state = ProjectState.load(instanceList.getCompound(i));
            data.instances.put(state.key().asString(), state);
        }
        CompoundTag rep = tag.getCompound("reputation");
        for (String key : rep.getAllKeys()) {
            data.reputation.put(key, rep.getInt(key));
        }
        CompoundTag pend = tag.getCompound("pending");
        for (String key : pend.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag rewards = pend.getList(key, Tag.TAG_COMPOUND);
                List<PendingReward> list = new ArrayList<>();
                for (int i = 0; i < rewards.size(); i++) {
                    list.add(PendingReward.load(rewards.getCompound(i)));
                }
                if (!list.isEmpty()) {
                    data.pending.put(uuid, list);
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed UUID key
            }
        }
        return data;
    }
}
