package dev.otectus.mcaquests.quest.situation.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    public long deadlineGameTime() {
        return deadlineGameTime;
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
        return now >= deadlineGameTime;
    }

    /** Ticks remaining until the master deadline at {@code now}, clamped to {@code >= 0}. */
    public long remainingTicks(long now) {
        return Math.max(0L, deadlineGameTime - now);
    }

    public Set<UUID> participants() {
        return participants;
    }

    public boolean addParticipant(UUID uuid) {
        return participants.add(uuid);
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
        ListTag participantList = new ListTag();
        participants.forEach(uuid -> participantList.add(StringTag.valueOf(uuid.toString())));
        tag.put("participants", participantList);
        return tag;
    }

    public static SituationInstance load(CompoundTag tag) {
        UUID villager = tag.contains("villager") ? tag.getUUID("villager") : null;
        UUID familyRoot = tag.contains("family_root") ? tag.getUUID("family_root") : null;
        SituationInstance instance = new SituationInstance(
                tag.getUUID("id"),
                new ResourceLocation(tag.getString("def")),
                tag.getInt("village_id"),
                villager,
                familyRoot,
                tag.getLong("open"),
                tag.getLong("deadline"),
                tag.getLong("seed"),
                SituationStatus.fromString(tag.getString("status")));
        ListTag participantList = tag.getList("participants", Tag.TAG_STRING);
        for (int i = 0; i < participantList.size(); i++) {
            try {
                instance.participants.add(UUID.fromString(participantList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed
            }
        }
        return instance;
    }
}
