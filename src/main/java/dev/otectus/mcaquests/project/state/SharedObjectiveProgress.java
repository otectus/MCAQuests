package dev.otectus.mcaquests.project.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared, server-owned progress for one project objective — the community analogue of the per-player
 * {@code ObjectiveProgress}. Keeps a single shared {@code count} (so event-driven objectives credit it
 * exactly like the quest system) plus a per-player contribution ledger (for "you: N" display and the
 * {@code CONTRIBUTORS}/{@code TOP_CONTRIBUTOR} reward targets) and a dedupe set used by the talk
 * objective.
 */
public final class SharedObjectiveProgress {

    private int count;
    private final Map<UUID, Integer> contributions = new HashMap<>();
    private final Set<UUID> talkedTo = new HashSet<>();

    public SharedObjectiveProgress() {
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

    public int contributionOf(UUID player) {
        return contributions.getOrDefault(player, 0);
    }

    public void addContribution(UUID player, int delta) {
        if (delta == 0) {
            return;
        }
        contributions.merge(player, delta, Integer::sum);
    }

    public Map<UUID, Integer> contributions() {
        return contributions;
    }

    /** Records that {@code villager} was interacted with; returns true if this is the first time. */
    public boolean markTalkedTo(UUID villager) {
        return talkedTo.add(villager);
    }

    public boolean hasTalkedTo(UUID villager) {
        return talkedTo.contains(villager);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        if (!contributions.isEmpty()) {
            CompoundTag c = new CompoundTag();
            contributions.forEach((uuid, amount) -> c.putInt(uuid.toString(), amount));
            tag.put("contributions", c);
        }
        if (!talkedTo.isEmpty()) {
            ListTag list = new ListTag();
            talkedTo.forEach(uuid -> list.add(StringTag.valueOf(uuid.toString())));
            tag.put("talked_to", list);
        }
        return tag;
    }

    public static SharedObjectiveProgress load(CompoundTag tag) {
        SharedObjectiveProgress progress = new SharedObjectiveProgress();
        progress.count = tag.getInt("count");
        if (tag.contains("contributions")) {
            CompoundTag c = tag.getCompound("contributions");
            for (String key : c.getAllKeys()) {
                try {
                    progress.contributions.put(UUID.fromString(key), c.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // skip a malformed UUID key rather than failing the whole load
                }
            }
        }
        if (tag.contains("talked_to")) {
            ListTag list = tag.getList("talked_to", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    progress.talkedTo.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        return progress;
    }
}
