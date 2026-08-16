package dev.otectus.mcaquests.quest.objective;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable per-objective progress, serialised with the owning {@code ActiveQuest}. For possession
 * objectives this stays empty (completion is computed live); accumulation objectives bump
 * {@link #count}.
 *
 * <p>The NPC/village objective types (escort, protect, build-near, cure, …) need a little more state
 * than a single counter: a duration accrual ({@link #elapsedTicks}), a locked target villager
 * ({@link #targetUuid}), a deduped set of placed block positions ({@link #visitedPositions}), a deduped
 * set of talked-to villagers ({@link #talkedTo}), and a free-form scratch bag ({@link #extra}) for the
 * rare flag. Every extra field is written to NBT only
 * when it carries a non-default value, so old saves (which only have {@code "count"}) load unchanged
 * and a plain counting objective serialises to exactly the same tag it always did.
 */
public final class ObjectiveProgress {

    private int count;
    private long elapsedTicks;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private Set<Long> visitedPositions;
    @Nullable
    private Set<UUID> talkedTo;
    @Nullable
    private CompoundTag extra;

    public ObjectiveProgress() {
        this(0);
    }

    public ObjectiveProgress(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }

    public void setCount(int value) {
        this.count = Math.max(0, value);
    }

    public void add(int delta) {
        setCount(this.count + delta);
    }

    // --- Duration accrual (protect_entity) -------------------------------------------------------

    public long elapsedTicks() {
        return elapsedTicks;
    }

    public void addElapsed(long delta) {
        this.elapsedTicks = Math.max(0L, this.elapsedTicks + delta);
    }

    public void resetElapsed() {
        this.elapsedTicks = 0L;
    }

    // --- Locked target (escort/protect/cure resolve once, then track the same villager) ----------

    @Nullable
    public UUID targetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(@Nullable UUID id) {
        this.targetUuid = id;
    }

    // --- Deduped block positions (build_near_location anti-farming) -------------------------------

    /** True if {@code pos} has already been credited. */
    public boolean hasVisited(BlockPos pos) {
        return visitedPositions != null && visitedPositions.contains(pos.asLong());
    }

    /** Records {@code pos}; returns true only on the first insert (so re-placing a block never re-credits). */
    public boolean addVisited(BlockPos pos) {
        if (visitedPositions == null) {
            visitedPositions = new HashSet<>();
        }
        return visitedPositions.add(pos.asLong());
    }

    public int visitedCount() {
        return visitedPositions == null ? 0 : visitedPositions.size();
    }

    // --- Deduped villagers (talk_to_profession counts *distinct* villagers) ------------------------

    /** True if {@code villager} has already been credited to this objective. */
    public boolean hasTalkedTo(UUID villager) {
        return talkedTo != null && talkedTo.contains(villager);
    }

    /** Records {@code villager}; returns true only on the first insert, so re-talking never re-credits. */
    public boolean markTalkedTo(UUID villager) {
        if (talkedTo == null) {
            talkedTo = new HashSet<>();
        }
        return talkedTo.add(villager);
    }

    // --- Free-form scratch (e.g. cure state flags) -----------------------------------------------

    /** A lazily-created scratch tag for per-objective flags the typed fields don't cover. */
    public CompoundTag extra() {
        if (extra == null) {
            extra = new CompoundTag();
        }
        return extra;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        if (elapsedTicks != 0L) {
            tag.putLong("elapsed", elapsedTicks);
        }
        if (targetUuid != null) {
            tag.putUUID("target", targetUuid);
        }
        if (visitedPositions != null && !visitedPositions.isEmpty()) {
            tag.putLongArray("visited", visitedPositions.stream().mapToLong(Long::longValue).toArray());
        }
        if (talkedTo != null && !talkedTo.isEmpty()) {
            ListTag list = new ListTag();
            talkedTo.forEach(uuid -> list.add(StringTag.valueOf(uuid.toString())));
            tag.put("talked_to", list);
        }
        if (extra != null && !extra.isEmpty()) {
            tag.put("extra", extra);
        }
        return tag;
    }

    public static ObjectiveProgress load(CompoundTag tag) {
        ObjectiveProgress progress = new ObjectiveProgress(tag.getInt("count"));
        progress.elapsedTicks = tag.getLong("elapsed"); // 0 when absent
        if (tag.hasUUID("target")) {
            progress.targetUuid = tag.getUUID("target");
        }
        if (tag.contains("visited", Tag.TAG_LONG_ARRAY)) {
            Set<Long> visited = new HashSet<>();
            for (long packed : tag.getLongArray("visited")) {
                visited.add(packed);
            }
            progress.visitedPositions = visited;
        }
        if (tag.contains("talked_to", Tag.TAG_LIST)) {
            Set<UUID> talked = new HashSet<>();
            ListTag list = tag.getList("talked_to", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    talked.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Drop an unparseable id rather than failing the whole quest load.
                }
            }
            progress.talkedTo = talked;
        }
        if (tag.contains("extra", Tag.TAG_COMPOUND)) {
            progress.extra = tag.getCompound("extra");
        }
        return progress;
    }
}
