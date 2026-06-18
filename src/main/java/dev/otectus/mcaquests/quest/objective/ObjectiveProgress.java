package dev.otectus.mcaquests.quest.objective;

import net.minecraft.nbt.CompoundTag;

/**
 * Mutable per-objective progress, serialised with the owning {@code ActiveQuest}. For possession
 * objectives this stays empty (completion is computed live); accumulation objectives bump
 * {@link #count}.
 */
public final class ObjectiveProgress {

    private int count;

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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        return tag;
    }

    public static ObjectiveProgress load(CompoundTag tag) {
        return new ObjectiveProgress(tag.getInt("count"));
    }
}
