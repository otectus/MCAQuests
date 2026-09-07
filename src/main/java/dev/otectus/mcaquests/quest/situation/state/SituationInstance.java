package dev.otectus.mcaquests.quest.situation.state;

import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One live, village-shared situation — the emergent analogue of {@code ProjectState} (0.8.0). Lives in
 * {@link SituationSavedData} (world storage), never on a player or villager, so it survives logout,
 * death, dimension change, villager unload/reload, and server restart. The focal villager / family and
 * the sponsoring village are referenced by id/UUID and re-resolved on demand.
 *
 * <p>The master {@code deadlineGameTime} is the single source of truth for when the situation closes;
 * per-acceptance quest deadlines are derived from it. {@code seed} makes any per-instance template
 * resolution deterministic and reproducible across a restart.
 */
public final class SituationInstance {

    private final UUID instanceId;
    private final ResourceLocation defId;
    private final int villageId;
    @Nullable
    private final UUID villagerUuid;
    @Nullable
    private final UUID familyRootUuid;
    private final long openGameTime;
    private final long deadlineGameTime;
    private final long seed;
    private SituationStatus status;
    private final Set<UUID> participants = new LinkedHashSet<>();
    /** Completed unavailable-content pauses, plus the start of an ongoing pause (both survive restart). */
    private long suspendedTicks;
    private long suspendedAtGameTime = -1L;
    private final Map<UUID, Set<CapitalsCapability>> participantRequirements = new LinkedHashMap<>();
    private boolean participantRequirementsTracked;

    public SituationInstance(UUID instanceId, ResourceLocation defId, int villageId,
                             @Nullable UUID villagerUuid, @Nullable UUID familyRootUuid,
                             long openGameTime, long deadlineGameTime, long seed, SituationStatus status) {
        this.instanceId = instanceId;
        this.defId = defId;
        this.villageId = villageId;
        this.villagerUuid = villagerUuid;
        this.familyRootUuid = familyRootUuid;
        this.openGameTime = openGameTime;
        this.deadlineGameTime = deadlineGameTime;
        this.seed = seed;
        this.status = status;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public ResourceLocation defId() {
        return defId;
    }

    public int villageId() {
        return villageId;
    }

    public Optional<UUID> villagerUuid() {
        return Optional.ofNullable(villagerUuid);
    }

    public Optional<UUID> familyRootUuid() {
        return Optional.ofNullable(familyRootUuid);
    }

    public long openGameTime() {
        return openGameTime;
    }

    /** Deadline after completed pauses; use {@link #remainingTicks} while a pause is still active. */
    public long deadlineGameTime() {
        return deadlineGameTime + suspendedTicks;
    }

    public long seed() {
        return seed;
    }

    public SituationStatus status() {
        return status;
    }

    public void setStatus(SituationStatus status) {
        this.status = status;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /** Whether the master deadline has passed at {@code now} (game time). */
    public boolean isExpiredAt(long now) {
        return now - suspendedTicks(now) >= deadlineGameTime;
    }

    /** Ticks remaining until the master deadline at {@code now}, clamped to {@code >= 0}. */
    public long remainingTicks(long now) {
        return Math.max(0L, deadlineGameTime - (now - suspendedTicks(now)));
    }

    /** Time this shared situation has spent waiting on unavailable accepted content. */
    public long suspendedTicks(long now) {
        return suspendedTicks + (suspendedAtGameTime < 0L ? 0L : Math.max(0L, now - suspendedAtGameTime));
    }

    /**
     * Pause time still owed to an accepted copy, including time its player spent offline. Only
     * previously credited shared pauses count here; unrelated local objective pauses remain separate.
     */
    public long missingSuspendedTicks(long questSituationSuspendedTicks, long now) {
        return Math.max(0L, suspendedTicks(now) - Math.max(0L, questSituationSuspendedTicks));
    }

    /** Starts or finishes a pause; the caller marks the containing saved data dirty on a change. */
    public boolean updateSuspension(long now, boolean suspended) {
        if (suspended && suspendedAtGameTime < 0L) {
            suspendedAtGameTime = now;
            return true;
        }
        if (!suspended && suspendedAtGameTime >= 0L) {
            suspendedTicks += Math.max(0L, now - suspendedAtGameTime);
            suspendedAtGameTime = -1L;
            return true;
        }
        return false;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public boolean addParticipant(UUID uuid) {
        return participants.add(uuid);
    }

    /** Historical participants remain available for outcomes; only active copies can pause a clock. */
    public boolean hasActiveParticipants() {
        return participantRequirementsTracked ? !participantRequirements.isEmpty() : !participants.isEmpty();
    }

    public Set<CapitalsCapability> participantRequirements(UUID participant) {
        return participantRequirements.getOrDefault(participant, Set.of());
    }

    public boolean setParticipantRequirements(UUID participant, Set<CapitalsCapability> required) {
        boolean changed = beginParticipantTracking();
        Set<CapitalsCapability> snapshot = Set.copyOf(required);
        return !snapshot.equals(participantRequirements.put(participant, snapshot)) || changed;
    }

    public boolean removeActiveParticipant(UUID participant) {
        boolean changed = beginParticipantTracking();
        return participantRequirements.remove(participant) != null || changed;
    }

    private boolean beginParticipantTracking() {
        if (participantRequirementsTracked) {
            return false;
        }
        // Older saves know only historical participants. Keep offline participants until their next
        // login can reconstruct active copies, rather than dropping them as another player logs in.
        participants.forEach(player -> participantRequirements.put(player, Set.of()));
        participantRequirementsTracked = true;
        return true;
    }

    public boolean needsUnavailableCapability(Predicate<CapitalsCapability> available) {
        return participantRequirements.values().stream().flatMap(Set::stream)
                .anyMatch(capability -> !available.test(capability));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", instanceId);
        tag.putString("def", defId.toString());
        tag.putInt("village_id", villageId);
        if (villagerUuid != null) {
            tag.putUUID("villager", villagerUuid);
        }
        if (familyRootUuid != null) {
            tag.putUUID("family_root", familyRootUuid);
        }
        tag.putLong("open", openGameTime);
        tag.putLong("deadline", deadlineGameTime);
        tag.putLong("seed", seed);
        tag.putString("status", status.lower());
        if (suspendedTicks > 0L) {
            tag.putLong("suspended_ticks", suspendedTicks);
        }
        if (suspendedAtGameTime >= 0L) {
            tag.putLong("suspended_at", suspendedAtGameTime);
        }
        ListTag participantList = new ListTag();
        participants.forEach(uuid -> participantList.add(StringTag.valueOf(uuid.toString())));
        tag.put("participants", participantList);
        if (participantRequirementsTracked) {
            CompoundTag active = new CompoundTag();
            participantRequirements.forEach((participant, required) -> {
                ListTag capabilities = new ListTag();
                required.stream().map(CapitalsCapability::id).sorted()
                        .forEach(id -> capabilities.add(StringTag.valueOf(id)));
                active.put(participant.toString(), capabilities);
            });
            tag.put("active_capitals_participants", active);
        }
        return tag;
    }

    public static SituationInstance load(CompoundTag tag) {
        UUID villager = tag.contains("villager") ? tag.getUUID("villager") : null;
        UUID familyRoot = tag.contains("family_root") ? tag.getUUID("family_root") : null;
        SituationInstance instance = new SituationInstance(
                tag.getUUID("id"),
                ResourceLocation.parse(tag.getString("def")),
                tag.getInt("village_id"),
                villager,
                familyRoot,
                tag.getLong("open"),
                tag.getLong("deadline"),
                tag.getLong("seed"),
                SituationStatus.fromString(tag.getString("status")));
        instance.suspendedTicks = Math.max(0L, tag.getLong("suspended_ticks"));
        instance.suspendedAtGameTime = tag.contains("suspended_at", Tag.TAG_LONG)
                ? tag.getLong("suspended_at") : -1L;
        ListTag participantList = tag.getList("participants", Tag.TAG_STRING);
        for (int i = 0; i < participantList.size(); i++) {
            try {
                instance.participants.add(UUID.fromString(participantList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed
            }
        }
        if (tag.contains("active_capitals_participants", Tag.TAG_COMPOUND)) {
            instance.participantRequirementsTracked = true;
            CompoundTag active = tag.getCompound("active_capitals_participants");
            for (String player : active.getAllKeys()) {
                try {
                    Set<CapitalsCapability> required = EnumSet.noneOf(CapitalsCapability.class);
                    ListTag ids = active.getList(player, Tag.TAG_STRING);
                    for (int i = 0; i < ids.size(); i++) {
                        String id = ids.getString(i);
                        for (CapitalsCapability capability : CapitalsCapability.values()) {
                            if (capability.id().equalsIgnoreCase(id)) required.add(capability);
                        }
                    }
                    instance.participantRequirements.put(UUID.fromString(player), Set.copyOf(required));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed player ids, as for historical participants above.
                }
            }
        }
        return instance;
    }
}
