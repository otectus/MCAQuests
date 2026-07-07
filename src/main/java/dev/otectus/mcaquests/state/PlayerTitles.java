package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's earned titles (spec 0.7.0): a set of global titles plus per-village title sets. Held inside
 * {@link PlayerQuestData} and serialised to player NBT. Purely additive — absent NBT loads as empty, so
 * pre-0.7.0 saves are unaffected.
 */
public final class PlayerTitles {

    private final Set<ResourceLocation> global = new LinkedHashSet<>();
    private final Map<Integer, Set<ResourceLocation>> byVillage = new LinkedHashMap<>();

    public Set<ResourceLocation> global() {
        return global;
    }

    public Map<Integer, Set<ResourceLocation>> byVillage() {
        return byVillage;
    }

    public Set<ResourceLocation> forVillage(int villageId) {
        return byVillage.getOrDefault(villageId, Set.of());
    }

    /** @return true if the title was newly added. */
    public boolean grantGlobal(ResourceLocation title) {
        return global.add(title);
    }

    /** @return true if the title was newly added for this village. */
    public boolean grantVillage(int villageId, ResourceLocation title) {
        return byVillage.computeIfAbsent(villageId, k -> new LinkedHashSet<>()).add(title);
    }

    public boolean hasGlobal(ResourceLocation title) {
        return global.contains(title);
    }

    public boolean hasVillage(int villageId, ResourceLocation title) {
        return byVillage.getOrDefault(villageId, Set.of()).contains(title);
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
        other.byVillage.forEach((id, set) -> byVillage.put(id, new LinkedHashSet<>(set)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag g = new ListTag();
        global.forEach(t -> g.add(StringTag.valueOf(t.toString())));
        tag.put("global", g);

        CompoundTag villages = new CompoundTag();
        byVillage.forEach((id, set) -> {
            ListTag list = new ListTag();
            set.forEach(t -> list.add(StringTag.valueOf(t.toString())));
            villages.put(Integer.toString(id), list);
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
            try {
                int id = Integer.parseInt(key);
                ListTag list = villages.getList(key, Tag.TAG_STRING);
                Set<ResourceLocation> set = new LinkedHashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                    if (rl != null) {
                        set.add(rl);
                    }
                }
                if (!set.isEmpty()) {
                    byVillage.put(id, set);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed village key
            }
        }
    }
}
