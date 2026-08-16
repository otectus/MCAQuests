package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player completion/cooldown history (spec section 16). Cooldowns are keyed per quest + giver
 * villager, so the same quest can be done at a different villager.
 *
 * <p>Completions and non-completion outcomes are tracked two ways: a <b>global</b> per-quest count
 * (the historical behaviour, used by standalone quests and {@code scope: global} conditions) and a
 * <b>per-giver</b> count keyed by quest + villager. The per-giver counts make relationship arcs
 * 1:1 — a chain advanced with one villager does not advance with another (chain prerequisites and
 * {@code scope: giver} conditions read these). Every record bumps both, so global behaviour is
 * unchanged and the per-giver data is purely additive (old saves load it as empty).
 */
public final class QuestHistory {

    /** Terminal states a quest can reach, recorded so chain conditions can branch on them. */
    public enum Outcome {
        COMPLETED, FAILED, ABANDONED
    }

    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private final Map<String, Integer> completions = new HashMap<>();
    // Per-quest counts for non-completion outcomes (FAILED / ABANDONED), keyed "<quest>|<OUTCOME>".
    private final Map<String, Integer> outcomes = new HashMap<>();
    // Per-giver completion counts, keyed "<quest>|<villager>" (per-villager relationship arcs).
    private final Map<String, Integer> completionsByGiver = new HashMap<>();
    // Per-giver non-completion outcomes, keyed "<quest>|<villager>|<OUTCOME>".
    private final Map<String, Integer> outcomesByGiver = new HashMap<>();

    private static String key(ResourceLocation quest, UUID villager) {
        return quest + "|" + villager;
    }

    private static String outcomeKey(ResourceLocation quest, Outcome outcome) {
        return quest + "|" + outcome.name();
    }

    private static String giverOutcomeKey(ResourceLocation quest, UUID villager, Outcome outcome) {
        return quest + "|" + villager + "|" + outcome.name();
    }

    public boolean onCooldown(ResourceLocation quest, UUID villager, long now) {
        Long until = cooldownUntil.get(key(quest, villager));
        return until != null && now < until;
    }

    public void setCooldownUntil(ResourceLocation quest, UUID villager, long gameTime) {
        cooldownUntil.put(key(quest, villager), gameTime);
    }

    /** Records a completion globally only. Prefer {@link #recordCompletion(ResourceLocation, UUID)}. */
    public void recordCompletion(ResourceLocation quest) {
        completions.merge(quest.toString(), 1, Integer::sum);
    }

    /** Records a completion both globally and against the giver villager (per-villager arcs). */
    public void recordCompletion(ResourceLocation quest, UUID villager) {
        recordCompletion(quest);
        completionsByGiver.merge(key(quest, villager), 1, Integer::sum);
    }

    public int completionCount(ResourceLocation quest) {
        return completions.getOrDefault(quest.toString(), 0);
    }

    /** A read-only view of all globally-completed quests and their counts (for the journal archive, 0.7.0). */
    public Map<ResourceLocation, Integer> completionsView() {
        Map<ResourceLocation, Integer> view = new java.util.LinkedHashMap<>();
        completions.forEach((k, v) -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            if (rl != null) {
                view.put(rl, v);
            }
        });
        return view;
    }

    /** Completions of {@code quest} turned in to {@code villager} specifically. */
    public int completionCountByGiver(ResourceLocation quest, UUID villager) {
        return completionsByGiver.getOrDefault(key(quest, villager), 0);
    }

    /** Total quests this player completed for {@code villager}, across every quest id (per-villager arcs). */
    public int completionCountByGiver(UUID villager) {
        String suffix = "|" + villager;
        int total = 0;
        for (Map.Entry<String, Integer> entry : completionsByGiver.entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** Total quest completions this player has recorded, across every quest id and villager. */
    public int totalCompletions() {
        return completions.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Records a non-completion outcome (FAILED / ABANDONED) globally only. */
    public void recordOutcome(ResourceLocation quest, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            recordCompletion(quest);
        } else {
            outcomes.merge(outcomeKey(quest, outcome), 1, Integer::sum);
        }
    }

    /** Records a non-completion outcome both globally and against the giver villager (per-villager arcs). */
    public void recordOutcome(ResourceLocation quest, UUID villager, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            recordCompletion(quest, villager);
            return;
        }
        recordOutcome(quest, outcome);
        outcomesByGiver.merge(giverOutcomeKey(quest, villager, outcome), 1, Integer::sum);
    }

    public int outcomeCount(ResourceLocation quest, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            return completionCount(quest);
        }
        return outcomes.getOrDefault(outcomeKey(quest, outcome), 0);
    }

    /** Outcomes of {@code quest} recorded against {@code villager} specifically. */
    public int outcomeCountByGiver(ResourceLocation quest, UUID villager, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            return completionCountByGiver(quest, villager);
        }
        return outcomesByGiver.getOrDefault(giverOutcomeKey(quest, villager, outcome), 0);
    }

    /** True when no completion, outcome, or cooldown has ever been recorded. */
    public boolean isEmpty() {
        return cooldownUntil.isEmpty() && completions.isEmpty() && outcomes.isEmpty()
                && completionsByGiver.isEmpty() && outcomesByGiver.isEmpty();
    }

    public void copyFrom(QuestHistory other) {
        cooldownUntil.clear();
        cooldownUntil.putAll(other.cooldownUntil);
        completions.clear();
        completions.putAll(other.completions);
        outcomes.clear();
        outcomes.putAll(other.outcomes);
        completionsByGiver.clear();
        completionsByGiver.putAll(other.completionsByGiver);
        outcomesByGiver.clear();
        outcomesByGiver.putAll(other.outcomesByGiver);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag cd = new CompoundTag();
        cooldownUntil.forEach(cd::putLong);
        tag.put("cooldowns", cd);
        CompoundTag done = new CompoundTag();
        completions.forEach(done::putInt);
        tag.put("completions", done);
        CompoundTag out = new CompoundTag();
        outcomes.forEach(out::putInt);
        tag.put("outcomes", out);
        CompoundTag doneByGiver = new CompoundTag();
        completionsByGiver.forEach(doneByGiver::putInt);
        tag.put("completions_by_giver", doneByGiver);
        CompoundTag outByGiver = new CompoundTag();
        outcomesByGiver.forEach(outByGiver::putInt);
        tag.put("outcomes_by_giver", outByGiver);
        return tag;
    }

    public void load(CompoundTag tag) {
        cooldownUntil.clear();
        completions.clear();
        outcomes.clear();
        completionsByGiver.clear();
        outcomesByGiver.clear();
        CompoundTag cd = tag.getCompound("cooldowns");
        cd.getAllKeys().forEach(k -> cooldownUntil.put(k, cd.getLong(k)));
        CompoundTag done = tag.getCompound("completions");
        done.getAllKeys().forEach(k -> completions.put(k, done.getInt(k)));
        CompoundTag out = tag.getCompound("outcomes"); // absent on pre-chain saves -> empty compound
        out.getAllKeys().forEach(k -> outcomes.put(k, out.getInt(k)));
        // Both absent on pre-per-villager saves -> empty maps (arcs restart per-villager; see CHANGELOG).
        CompoundTag doneByGiver = tag.getCompound("completions_by_giver");
        doneByGiver.getAllKeys().forEach(k -> completionsByGiver.put(k, doneByGiver.getInt(k)));
        CompoundTag outByGiver = tag.getCompound("outcomes_by_giver");
        outByGiver.getAllKeys().forEach(k -> outcomesByGiver.put(k, outByGiver.getInt(k)));
    }
}
