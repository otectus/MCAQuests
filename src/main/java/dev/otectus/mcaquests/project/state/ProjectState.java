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
import java.util.OptionalLong;
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
    private long startGameTime;
    private OptionalLong startDayTime = OptionalLong.empty();
    private long suspendedTicks;
    private long lastClockSample = Long.MIN_VALUE;
    /** How far missing-definition outages have been credited from {@code ContentOutageData} (1.7.0); -1 before. */
    private long outageAccountedUntil = -1L;
    /** Fingerprints of the current phase's objectives as it opened (1.7.0; see {@code ProjectDrift}). */
    private java.util.List<String> phaseFingerprints = java.util.List.of();
    private long retryAt = Long.MAX_VALUE;

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
    /**
     * The anchor radius this instance was created with, frozen so a later change to
     * {@code defaultScopeFallbackRadius} cannot silently resize a village's build area (1.7.0). Empty on
     * saves from before 1.7.0 until {@code ProjectManager} freezes the radius they were actually using.
     */
    private OptionalInt anchorRadius = OptionalInt.empty();
    /**
     * Bumped on every phase entry, status change and operator repair. A repair preview names the revision
     * it saw, so a confirmation against a project that has moved on since is refused rather than applied
     * to state its operator never looked at.
     */
    private long revision;
    /**
     * Project-level scratch, the per-instance analogue of {@code SharedObjectiveProgress.extra()}: the
     * spirit reading taken when the project began, its pending flag, and migration markers.
     */
    private CompoundTag extra = new CompoundTag();
    /**
     * Follow-up projects that could not be seeded when this one finished because their optional mod was
     * missing. Seeded on a later sweep once they load; never dropped (1.7.0).
     */
    private final Set<ResourceLocation> deferredFollowUps = new LinkedHashSet<>();

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

    public void setStartDayTime(long dayTime) { startDayTime = OptionalLong.of(dayTime); }

    /** Pre-stabilization projects never ran failure clocks; give those saves a fresh grace period. */
    public void initializeFailureClock(long gameTime, long dayTime) {
        if (startDayTime.isEmpty()) {
            startGameTime = gameTime;
            startDayTime = OptionalLong.of(dayTime);
            suspendedTicks = 0;
            lastClockSample = gameTime;
        }
    }

    public OptionalLong startDayTime() { return startDayTime; }

    public long suspendedTicks() { return suspendedTicks; }

    /** Accounts for unavailable/paused time once per server sweep. */
    public void sampleClock(long now, boolean suspended) {
        if (lastClockSample != Long.MIN_VALUE && suspended && now > lastClockSample) {
            long elapsed = now - lastClockSample;
            suspendedTicks = suspendedTicks > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : suspendedTicks + elapsed;
        }
        lastClockSample = now;
    }

    /**
     * Credits the time since the last sweep during which this project's definition was missing, whoever
     * was online (1.7.0), and says whether the ledger is tracking it as missing now. The sweep runs only
     * while someone is online, so an outage nobody saw would otherwise run the deadline down.
     */
    public boolean creditOutage(dev.otectus.mcaquests.state.ContentOutageData ledger, long now) {
        String key = dev.otectus.mcaquests.state.ContentOutageData.projectKey(projectId);
        if (outageAccountedUntil >= 0L && now > outageAccountedUntil) {
            long credit = ledger.overlap(key, outageAccountedUntil, now);
            suspendedTicks = suspendedTicks > Long.MAX_VALUE - credit ? Long.MAX_VALUE : suspendedTicks + credit;
        }
        outageAccountedUntil = now;
        return ledger.covers(key);
    }

    public java.util.List<String> phaseFingerprints() { return phaseFingerprints; }

    public void setPhaseFingerprints(java.util.List<String> fingerprints) {
        this.phaseFingerprints = java.util.List.copyOf(fingerprints);
    }

    public void allowRetryAt(long gameTime) { retryAt = gameTime; }

    public boolean canRetry(long now) {
        return status == ProjectStatus.FAILED && retryAt != Long.MAX_VALUE && now >= retryAt;
    }

    public int currentPhase() {
        return currentPhase;
    }

    public List<SharedObjectiveProgress> progress() {
        return progress;
    }

    public SharedObjectiveProgress progress(int index) {
        while (index >= progress.size()) {
            progress.add(new SharedObjectiveProgress());
        }
        return progress.get(index);
    }

    public int progressCount() {
        return progress.size();
    }

    /** Advances to {@code phase} with a fresh, correctly-sized shared progress list. */
    public void enterPhase(int phase, int objectiveCount) {
        this.currentPhase = phase;
        this.progress = freshProgress(objectiveCount);
        this.revision++;
    }

    public OptionalInt anchorRadius() {
        return anchorRadius;
    }

    /** Freezes the anchor radius once; later calls leave the first value in place. */
    public boolean freezeAnchorRadius(int radius) {
        if (anchorRadius.isPresent()) {
            return false;
        }
        anchorRadius = OptionalInt.of(radius);
        return true;
    }

    public long revision() {
        return revision;
    }

    public void bumpRevision() {
        revision++;
    }

    public CompoundTag extra() {
        return extra;
    }

    public Set<ResourceLocation> deferredFollowUps() {
        return deferredFollowUps;
    }

    /**
     * A copy of this instance under another identity string, for the one-time dimension re-key of
     * pre-1.7.0 saves. Everything else — progress, sponsors, ledgers — is carried over exactly.
     */
    public ProjectState rekeyed(String newIdentity) {
        CompoundTag copy = save();
        copy.putString("identity", newIdentity);
        return load(copy);
    }

    /**
     * A copy of this instance bound to another place (1.7.0): the operator's repair for an instance whose
     * MCA village was deleted or merged. {@code village} empty makes it anchor-bound at {@code anchor};
     * present, it belongs to that village. Progress, sponsors, ledgers and owed rewards are carried over.
     */
    public ProjectState rebound(String newIdentity, OptionalInt village, BlockPos anchor) {
        CompoundTag copy = save();
        copy.putString("identity", newIdentity);
        copy.remove("village_id");
        village.ifPresent(id -> copy.putInt("village_id", id));
        copy.putLong("anchor", anchor.asLong());
        ProjectState moved = load(copy);
        moved.bumpRevision();
        return moved;
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
        if (this.status != status) {
            revision++;
        }
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
        startDayTime.ifPresent(value -> tag.putLong("start_day", value));
        if (suspendedTicks != 0L) { tag.putLong("suspended_ticks", suspendedTicks); }
        if (lastClockSample != Long.MIN_VALUE) { tag.putLong("clock_sample", lastClockSample); }
        if (outageAccountedUntil >= 0L) { tag.putLong("outage_accounted", outageAccountedUntil); }
        if (!phaseFingerprints.isEmpty()) {
            ListTag fingerprints = new ListTag();
            phaseFingerprints.forEach(fp -> fingerprints.add(net.minecraft.nbt.StringTag.valueOf(fp)));
            tag.put("phase_fp", fingerprints);
        }
        if (retryAt != Long.MAX_VALUE) { tag.putLong("retry_at", retryAt); }
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
        anchorRadius.ifPresent(radius -> tag.putInt("anchor_radius", radius));
        if (revision != 0L) { tag.putLong("revision", revision); }
        if (!extra.isEmpty()) { tag.put("extra", extra.copy()); }
        if (!deferredFollowUps.isEmpty()) {
            ListTag deferred = new ListTag();
            deferredFollowUps.forEach(id -> deferred.add(StringTag.valueOf(id.toString())));
            tag.put("deferred_follow_ups", deferred);
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
        if (tag.contains("start_day")) { state.startDayTime = OptionalLong.of(tag.getLong("start_day")); }
        state.suspendedTicks = Math.max(0L, tag.getLong("suspended_ticks"));
        if (tag.contains("clock_sample")) { state.lastClockSample = tag.getLong("clock_sample"); }
        if (tag.contains("outage_accounted")) { state.outageAccountedUntil = tag.getLong("outage_accounted"); }
        ListTag phaseFp = tag.getList("phase_fp", Tag.TAG_STRING);
        java.util.List<String> fingerprints = new java.util.ArrayList<>(phaseFp.size());
        for (int i = 0; i < phaseFp.size(); i++) {
            fingerprints.add(phaseFp.getString(i));
        }
        state.phaseFingerprints = java.util.List.copyOf(fingerprints);
        if (tag.contains("retry_at")) { state.retryAt = tag.getLong("retry_at"); }
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
        if (tag.contains("anchor_radius", Tag.TAG_INT)) { state.anchorRadius = OptionalInt.of(tag.getInt("anchor_radius")); }
        state.revision = tag.getLong("revision");
        if (tag.contains("extra", Tag.TAG_COMPOUND)) { state.extra = tag.getCompound("extra").copy(); }
        ListTag deferred = tag.getList("deferred_follow_ups", Tag.TAG_STRING);
        for (int i = 0; i < deferred.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(deferred.getString(i));
            if (id != null) {
                state.deferredFollowUps.add(id);
            }
        }
        return state;
    }
}
