package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-player lifetime progression counters (spec section 11.2): a small additive struct held inside
 * {@link PlayerQuestData} alongside {@link QuestHistory} and {@link PlayerTitles}. Incremented server-side,
 * inline at the same lifecycle funnels that post the corresponding events (situation resolution success,
 * project completion, banked contributions) — never via an event listener, so no path can double-count
 * or lag a tick behind. Purely additive — absent NBT loads as empty, so pre-1.0.0 saves are unaffected.
 */
public final class ProgressionStats {

    /** Keyed by situation SOURCE definition id (the id {@code SituationResolvedEvent} carries), success only. */
    private final Map<ResourceLocation, Integer> situationSuccesses = new LinkedHashMap<>();
    /** Keyed by project definition id, +1 per online participant when a project completes. */
    private final Map<ResourceLocation, Integer> projectCompletions = new LinkedHashMap<>();
    /** Keyed by project definition id, summing contributed units for the contributing player. */
    private final Map<ResourceLocation, Integer> projectContributions = new LinkedHashMap<>();

    public Map<ResourceLocation, Integer> situationSuccesses() {
        return situationSuccesses;
    }

    public Map<ResourceLocation, Integer> projectCompletions() {
        return projectCompletions;
    }

    public Map<ResourceLocation, Integer> projectContributions() {
        return projectContributions;
    }

    /** Adds {@code amount} to {@code map}'s entry for {@code id} (creating it at {@code amount} if absent). */
    public static void increment(Map<ResourceLocation, Integer> map, ResourceLocation id, int amount) {
        if (amount == 0) {
            return;
        }
        map.merge(id, amount, Integer::sum);
    }

    /** The current counter for {@code id}, or 0 if never incremented. */
    public static int count(Map<ResourceLocation, Integer> map, ResourceLocation id) {
        return map.getOrDefault(id, 0);
    }

    /** The sum of every entry in {@code map}. */
    public static int total(Map<ResourceLocation, Integer> map) {
        int sum = 0;
        for (int v : map.values()) {
            sum += v;
        }
        return sum;
    }

    public boolean isEmpty() {
        return situationSuccesses.isEmpty() && projectCompletions.isEmpty() && projectContributions.isEmpty();
    }

    public void clear() {
        situationSuccesses.clear();
        projectCompletions.clear();
        projectContributions.clear();
    }

    public void copyFrom(ProgressionStats other) {
        clear();
        situationSuccesses.putAll(other.situationSuccesses);
        projectCompletions.putAll(other.projectCompletions);
        projectContributions.putAll(other.projectContributions);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("situation_successes", saveMap(situationSuccesses));
        tag.put("project_completions", saveMap(projectCompletions));
        tag.put("project_contributions", saveMap(projectContributions));
        return tag;
    }

    private static CompoundTag saveMap(Map<ResourceLocation, Integer> map) {
        CompoundTag out = new CompoundTag();
        map.forEach((id, count) -> out.putInt(id.toString(), count));
        return out;
    }

    public void load(CompoundTag tag) {
        clear();
        // All three keys are absent on pre-1.0.0 saves -> each loads as an empty compound -> empty map.
        loadMap(tag.getCompound("situation_successes"), situationSuccesses);
        loadMap(tag.getCompound("project_completions"), projectCompletions);
        loadMap(tag.getCompound("project_contributions"), projectContributions);
    }

    private static void loadMap(CompoundTag tag, Map<ResourceLocation, Integer> map) {
        for (String key : tag.getAllKeys()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null) {
                map.put(rl, tag.getInt(key));
            }
        }
    }
}
