package dev.otectus.mcaquests.project.state;

import dev.otectus.mcaquests.project.ProjectScope;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * One live, shared project instance — the community analogue of {@code ActiveQuest}. Lives in
 * {@link ProjectSavedData} (world storage), never on a player or villager, so it survives logout,
 * death, dimension change, villager unload/reload, and server restart. Sponsors and the village are
 * referenced by id/UUID and re-resolved on demand.
 */
public final class ProjectState {

    private final ResourceLocation projectId;
    private final ProjectScope scope;
    private final String identity;
    private final ResourceLocation anchorDimension;
    private final BlockPos anchorPos;
    private final OptionalInt villageId;
    private final long startGameTime;

    private int currentPhase;
    private List<SharedObjectiveProgress> progress;
    private final Set<UUID> sponsors = new LinkedHashSet<>();
    /** Every player who has contributed at any phase — used for the all_participants reward target. */
    private final Set<UUID> participants = new LinkedHashSet<>();
    private ProjectStatus status = ProjectStatus.ACTIVE;
    /** Phases whose rewards have already been distributed (one-shot guard against double payout). */
    private final BitSet phaseRewardsDistributed = new BitSet();
    /**
     * Randomized phase-reward amounts rolled once and shared by every recipient, keyed
     * {@code "<phase>:<rewardIndex>"} — currently only {@code mcaquests:currency}. Persisted so a player
     * who collects a banked reward after logging back in is paid the same amount as everyone who was
     * online at the time. Absent on pre-1.1.0 saves and on projects with no randomized reward.
     */
    private final Map<String, Integer> frozenRewards = new HashMap<>();

    public ProjectState(ResourceLocation projectId, ProjectScope scope, String identity,
                        ResourceLocation anchorDimension, BlockPos anchorPos, OptionalInt villageId,
                        long startGameTime, int firstPhaseObjectiveCount) {
        this.projectId = projectId;
        this.scope = scope;
        this.identity = identity;
        this.anchorDimension = anchorDimension;
        this.anchorPos = anchorPos;
        this.villageId = villageId;
        this.startGameTime = startGameTime;
        this.currentPhase = 0;
        this.progress = freshProgress(firstPhaseObjectiveCount);
    }

    private ProjectState(ResourceLocation projectId, ProjectScope scope, String identity,
                         ResourceLocation anchorDimension, BlockPos anchorPos, OptionalInt villageId,
                         long startGameTime, int currentPhase, List<SharedObjectiveProgress> progress) {
        this.projectId = projectId;
        this.scope = scope;
        this.identity = identity;
        this.anchorDimension = anchorDimension;
        this.anchorPos = anchorPos;
        this.villageId = villageId;
        this.startGameTime = startGameTime;
        this.currentPhase = currentPhase;
        this.progress = progress;
    }

    private static List<SharedObjectiveProgress> freshProgress(int objectiveCount) {
        List<SharedObjectiveProgress> list = new ArrayList<>();
        for (int i = 0; i < objectiveCount; i++) {
            list.add(new SharedObjectiveProgress());
        }
        return list;
    }

    public ProjectInstanceKey key() {
        return new ProjectInstanceKey(projectId, scope, identity);
    }

    public ResourceLocation projectId() {
        return projectId;
    }

    public ProjectScope scope() {
        return scope;
    }

    public String identity() {
        return identity;
    }

    public ResourceLocation anchorDimension() {
        return anchorDimension;
    }

    public BlockPos anchorPos() {
        return anchorPos;
    }

    public OptionalInt villageId() {
        return villageId;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public int currentPhase() {
        return currentPhase;
    }

    public List<SharedObjectiveProgress> progress() {
        return progress;
    }

    public SharedObjectiveProgress progress(int index) {
        return progress.get(index);
    }

    public int progressCount() {
        return progress.size();
    }

    /** Advances to {@code phase} with a fresh, correctly-sized shared progress list. */
    public void enterPhase(int phase, int objectiveCount) {
        this.currentPhase = phase;
        this.progress = freshProgress(objectiveCount);
    }

    public Set<UUID> sponsors() {
        return sponsors;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public void addParticipant(UUID uuid) {
        participants.add(uuid);
    }

    public void addSponsor(UUID uuid) {
        sponsors.add(uuid);
    }

    public boolean removeSponsor(UUID uuid) {
        return sponsors.remove(uuid);
    }

    public boolean hasSponsor(UUID uuid) {
        return sponsors.contains(uuid);
    }

    public ProjectStatus status() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    /** Atomically marks {@code phase}'s rewards distributed; returns true only for the first caller. */
    public boolean tryMarkPhaseDistributed(int phase) {
        if (phaseRewardsDistributed.get(phase)) {
            return false;
        }
        phaseRewardsDistributed.set(phase);
        return true;
    }

    public boolean isPhaseDistributed(int phase) {
        return phaseRewardsDistributed.get(phase);
    }

    /**
     * Rolls and stores {@code amount} for one randomized phase reward if nothing is stored yet, returning
     * the stored value either way. Keyed by phase <em>and</em> reward index so every recipient of a shared
     * phase reward — including a player who was offline and collects it later — is paid the same number,
     * and so re-entering the distribution path can never roll a second time.
     */
    public int freezeReward(int phase, int rewardIndex, int amount) {
        return frozenRewards.computeIfAbsent(phase + ":" + rewardIndex, k -> amount);
    }

    /** The amount frozen for one phase reward, or empty if it has none. */
    public OptionalInt frozenReward(int phase, int rewardIndex) {
        Integer value = frozenRewards.get(phase + ":" + rewardIndex);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /** Every player who has contributed to any objective of the current phase. */
    public Set<UUID> currentPhaseContributors() {
        Set<UUID> out = new LinkedHashSet<>();
        for (SharedObjectiveProgress p : progress) {
            out.addAll(p.contributions().keySet());
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("project", projectId.toString());
        tag.putString("scope", scope.lower());
        tag.putString("identity", identity);
        tag.putString("anchor_dim", anchorDimension.toString());
        tag.putLong("anchor", anchorPos.asLong());
        villageId.ifPresent(id -> tag.putInt("village_id", id));
        tag.putLong("start", startGameTime);
        tag.putInt("phase", currentPhase);
        tag.putString("status", status.lower());
        tag.putByteArray("distributed", phaseRewardsDistributed.toByteArray());
        ListTag sponsorList = new ListTag();
        sponsors.forEach(uuid -> sponsorList.add(StringTag.valueOf(uuid.toString())));
        tag.put("sponsors", sponsorList);
        ListTag participantList = new ListTag();
        participants.forEach(uuid -> participantList.add(StringTag.valueOf(uuid.toString())));
        tag.put("participants", participantList);
        ListTag progressList = new ListTag();
        for (SharedObjectiveProgress p : progress) {
            progressList.add(p.save());
        }
        tag.put("progress", progressList);
        if (!frozenRewards.isEmpty()) {
            CompoundTag frozen = new CompoundTag();
            frozenRewards.forEach(frozen::putInt);
            tag.put("frozen_rewards", frozen);
        }
        return tag;
    }

    public static ProjectState load(CompoundTag tag) {
        List<SharedObjectiveProgress> progress = new ArrayList<>();
        ListTag progressList = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < progressList.size(); i++) {
            progress.add(SharedObjectiveProgress.load(progressList.getCompound(i)));
        }
        OptionalInt villageId = tag.contains("village_id") ? OptionalInt.of(tag.getInt("village_id")) : OptionalInt.empty();
        ProjectScope scope;
        try {
            scope = ProjectScope.valueOf(tag.getString("scope").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            scope = ProjectScope.VILLAGE;
        }
        ProjectState state = new ProjectState(
                ResourceLocation.parse(tag.getString("project")),
                scope,
                tag.getString("identity"),
                ResourceLocation.parse(tag.getString("anchor_dim")),
                BlockPos.of(tag.getLong("anchor")),
                villageId,
                tag.getLong("start"),
                tag.getInt("phase"),
                progress);
        state.status = ProjectStatus.fromString(tag.getString("status"));
        state.phaseRewardsDistributed.or(BitSet.valueOf(tag.getByteArray("distributed")));
        ListTag sponsorList = tag.getList("sponsors", Tag.TAG_STRING);
        for (int i = 0; i < sponsorList.size(); i++) {
            try {
                state.sponsors.add(UUID.fromString(sponsorList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed
            }
        }
        ListTag participantList = tag.getList("participants", Tag.TAG_STRING);
        for (int i = 0; i < participantList.size(); i++) {
            try {
                state.participants.add(UUID.fromString(participantList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed
            }
        }
        if (tag.contains("frozen_rewards", Tag.TAG_COMPOUND)) {
            CompoundTag frozen = tag.getCompound("frozen_rewards");
            frozen.getAllKeys().forEach(key -> state.frozenRewards.put(key, frozen.getInt(key)));
        }
        return state;
    }
}
