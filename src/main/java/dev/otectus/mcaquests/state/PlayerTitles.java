package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The titles this player has earned (spec 0.7.0): global ones, and per-village ones.
 *
 * <h2>Villages are keyed by dimension</h2>
 *
 * <p>MCA allocates village ids per level, so a bare integer names two different places in a world
 * with a Nether village. Village titles are therefore keyed {@code <dimension>|<id>} — the same
 * identity rule every other standing store in this suite uses. Legacy saves that stored a bare
 * integer are read as the overworld, which is the only thing they could have meant in practice
 * (the same assumption §32.2 codifies for the legacy score migration).
 */
public final class PlayerTitles {

    private static final String OVERWORLD = "minecraft:overworld";

    private final Set<ResourceLocation> global = new LinkedHashSet<>();

    /** {@code "<dimension>|<villageId>"} → titles. See the class comment for the key rule. */
    private final Map<String, Set<ResourceLocation>> byVillage = new LinkedHashMap<>();

    public Set<ResourceLocation> global() {
        return global;
    }

    /** The canonical village key. A null dimension reads as the overworld, like a legacy save. */
    public static String villageKey(@Nullable ResourceLocation dimension, int villageId) {
        return (dimension == null ? OVERWORLD : dimension.toString()) + "|" + villageId;
    }

    /**
     * The whole per-village map, keyed {@code "<dimension>|<id>"}. For diagnostics and "any village"
     * scans; village-specific reads go through {@link #forVillage}.
     */
    public Map<String, Set<ResourceLocation>> byVillage() {
        return byVillage;
    }

    /** The village ids this player holds titles with in one dimension. */
    public Set<Integer> villageIdsIn(ResourceLocation dimension) {
        String prefix = (dimension == null ? OVERWORLD : dimension.toString()) + "|";
        Set<Integer> ids = new LinkedHashSet<>();
        for (String key : byVillage.keySet()) {
            if (key.startsWith(prefix)) {
                try {
                    ids.add(Integer.parseInt(key.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // malformed key; unreachable through the API, skipped if hand-edited
                }
            }
        }
        return ids;
    }

    public Set<ResourceLocation> forVillage(@Nullable ResourceLocation dimension, int villageId) {
        return byVillage.getOrDefault(villageKey(dimension, villageId), Set.of());
    }

    /** @return true if the title was newly added. */
    public boolean grantGlobal(ResourceLocation title) {
        return global.add(title);
    }

    /** @return true if the title was newly added for this village. */
    public boolean grantVillage(@Nullable ResourceLocation dimension, int villageId, ResourceLocation title) {
        return byVillage.computeIfAbsent(villageKey(dimension, villageId), k -> new LinkedHashSet<>())
                .add(title);
    }

    public boolean hasGlobal(ResourceLocation title) {
        return global.contains(title);
    }

    public boolean hasVillage(@Nullable ResourceLocation dimension, int villageId, ResourceLocation title) {
        return byVillage.getOrDefault(villageKey(dimension, villageId), Set.of()).contains(title);
    }

    public boolean isEmpty() {
        return global.isEmpty() && byVillage.isEmpty();
    }

    public void clear() {
        global.clear();
        byVillage.clear();
    }

    public void copyFrom(PlayerTitles other) {
        clear();
        global.addAll(other.global);
        other.byVillage.forEach((key, set) -> byVillage.put(key, new LinkedHashSet<>(set)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag g = new ListTag();
        global.forEach(t -> g.add(StringTag.valueOf(t.toString())));
        tag.put("global", g);

        CompoundTag villages = new CompoundTag();
        byVillage.forEach((key, set) -> {
            ListTag list = new ListTag();
            set.forEach(t -> list.add(StringTag.valueOf(t.toString())));
            villages.put(key, list);
        });
        tag.put("villages", villages);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        ListTag g = tag.getList("global", Tag.TAG_STRING);
        for (int i = 0; i < g.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(g.getString(i));
            if (rl != null) {
                global.add(rl);
            }
        }
        CompoundTag villages = tag.getCompound("villages");
        for (String key : villages.getAllKeys()) {
            String normalized = normalizeKey(key);
            if (normalized == null) {
                continue; // malformed village key; skipped, never fatal
            }
            ListTag list = villages.getList(key, Tag.TAG_STRING);
            Set<ResourceLocation> set = byVillage.computeIfAbsent(normalized, k -> new LinkedHashSet<>());
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null) {
                    set.add(rl);
                }
            }
            if (set.isEmpty()) {
                byVillage.remove(normalized);
            }
        }
    }

    /**
     * Accepts both key generations: {@code "<dimension>|<id>"} as written since dimension-aware
     * keying, and a bare integer from an older save — read as the overworld (§32.2's assumption).
     */
    @Nullable
    private static String normalizeKey(String key) {
        int separator = key.lastIndexOf('|');
        if (separator > 0) {
            try {
                Integer.parseInt(key.substring(separator + 1));
                return key;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return villageKey(null, Integer.parseInt(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}