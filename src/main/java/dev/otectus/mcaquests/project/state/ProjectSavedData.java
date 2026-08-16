package dev.otectus.mcaquests.project.state;

import dev.otectus.mcaquests.McaQuests;
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
    private final Map<String, String> tierHighWater = new LinkedHashMap<>();
    private final Map<UUID, List<PendingReward>> pending = new LinkedHashMap<>();
    private dev.otectus.mcaquests.state.VillageStanding standing =
            new dev.otectus.mcaquests.state.VillageStanding();

    public ProjectSavedData() {
    }

    public static ProjectSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        // DATA_NAME is unchanged from 1.20.1 — the .dat file is loader-independent, so existing
        // worlds keep their projects/standing through the upgrade. DataFixTypes null: no vanilla DFU applies.
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProjectSavedData::new,
                        (tag, provider) -> ProjectSavedData.load(tag),
                        null),
                DATA_NAME);
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

    // --- village standing v2 (per player, dimension-aware; spec 1.1.0 §29.2) ---

    /**
     * The per-player, dimension-aware standing store. This is what every reputation read and write
     * goes through from 1.1.0 onward, whether Quests owns the number itself or is mirroring MCA:
     * Reputation's canonical state.
     *
     * <p>The v1 accessors below are retained but are no longer a live gameplay path: they exist so the
     * one-time migration can read them and so an administrator can recover a pre-1.1.0 world by hand
     * (§32.3). {@code ReputationAssertionsTest} fails the build if a gameplay call site starts using
     * them again.
     */
    public dev.otectus.mcaquests.state.VillageStanding standing() {
        return standing;
    }

    /** Marks the store dirty after a standing mutation. Callers must use this, not {@code setDirty}. */
    public void standingChanged() {
        setDirty();
    }

    // --- reputation v1 (LEGACY: world-shared, dimension-blind; retained read-only) ---

    /**
     * @deprecated v1 shared-scope reputation. Superseded by {@link #standing()}, which is per player
     *         and dimension-aware. Retained only so the one-time migration can read it and so a
     *         pre-1.1.0 world stays hand-recoverable (§32.3); never write to it from gameplay.
     */
    @Deprecated
    public int reputation(String identity) {
        return reputation.getOrDefault(identity, 0);
    }

    /**
     * A snapshot of all v1 scope identities that carry a legacy reputation value.
     *
     * @deprecated see {@link #reputation(String)}; migration and manual recovery only.
     */
    @Deprecated
    public java.util.Set<String> reputationKeys() {
        return new java.util.LinkedHashSet<>(reputation.keySet());
    }

    /** @deprecated see {@link #reputation(String)}; migration and manual recovery only. */
    @Deprecated
    public void addReputation(String identity, int delta) {
        if (delta == 0) {
            return;
        }
        reputation.merge(identity, delta, Integer::sum);
        setDirty();
    }

    /**
     * Highest v1 tier id ever reached for this scope identity, or {@code null}.
     *
     * @deprecated see {@link #reputation(String)}; migration and manual recovery only.
     */
    @Deprecated
    public String tierHighWater(String identity) {
        return tierHighWater.get(identity);
    }

    /** @deprecated see {@link #reputation(String)}; migration and manual recovery only. */
    @Deprecated
    public void setTierHighWater(String identity, String tierId) {
        if (!tierId.equals(tierHighWater.get(identity))) {
            tierHighWater.put(identity, tierId);
            setDirty();
        }
    }

    // --- offline pending player rewards ---

    public void addPending(UUID player, PendingReward reward) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(reward);
        setDirty();
    }

    /** Convenience overload for banked FTB-claim rewards (task M3.1) — routes through {@link #addPending}. */
    public void addBankedReward(UUID player, BankedReward reward) {
        addPending(player, PendingReward.ofBanked(reward));
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
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag instanceList = new ListTag();
        for (ProjectState state : instances.values()) {
            instanceList.add(state.save());
        }
        tag.put("instances", instanceList);

        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        tag.put("reputation", rep);

        CompoundTag hw = new CompoundTag();
        tierHighWater.forEach(hw::putString);
        tag.put("repTierHW", hw);

        // v2 per-player, dimension-aware standing. Written alongside the v1 tags above, never
        // instead of them: 32.3 requires the legacy compounds retained for rollback and manual
        // recovery, and they cost nothing once migration has stopped reading them.
        if (!standing.isEmpty()) {
            tag.put("standingV2", standing.save());
        }

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
        CompoundTag hw = tag.getCompound("repTierHW");
        for (String key : hw.getAllKeys()) {
            data.tierHighWater.put(key, hw.getString(key));
        }
        data.standing = dev.otectus.mcaquests.state.VillageStanding.load(tag.getCompound("standingV2"));
        CompoundTag pend = tag.getCompound("pending");
        for (String key : pend.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag rewards = pend.getList(key, Tag.TAG_COMPOUND);
                List<PendingReward> list = new ArrayList<>();
                for (int i = 0; i < rewards.size(); i++) {
                    // Per-entry guard (forward-compat, task M3.1): an unrecognised kind/type, or a
                    // malformed tag that throws while parsing (e.g. an invalid ResourceLocation), is
                    // skipped without dropping this player's other, well-formed pending rewards.
                    // Fail-safe per §10.2: catch Throwable, log at DEBUG, skip the entry.
                    try {
                        PendingReward.load(rewards.getCompound(i)).ifPresent(list::add);
                    } catch (Throwable t) {
                        McaQuests.LOGGER.debug("[MCA: Quests] skipping malformed pending-reward entry for {}",
                                key, t);
                    }
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
