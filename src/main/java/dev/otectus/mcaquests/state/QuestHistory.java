package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player completion/cooldown history (spec section 16). Cooldowns are keyed per quest + giver
 * villager, so the same quest can be done at a different villager.
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

    private static String key(ResourceLocation quest, UUID villager) {
        return quest + "|" + villager;
    }

    private static String outcomeKey(ResourceLocation quest, Outcome outcome) {
        return quest + "|" + outcome.name();
    }

    public boolean onCooldown(ResourceLocation quest, UUID villager, long now) {
        Long until = cooldownUntil.get(key(quest, villager));
        return until != null && now < until;
    }

    public void setCooldownUntil(ResourceLocation quest, UUID villager, long gameTime) {
        cooldownUntil.put(key(quest, villager), gameTime);
    }

    public void recordCompletion(ResourceLocation quest) {
        completions.merge(quest.toString(), 1, Integer::sum);
    }

    public int completionCount(ResourceLocation quest) {
        return completions.getOrDefault(quest.toString(), 0);
    }

    /** Records a non-completion outcome (FAILED / ABANDONED). COMPLETED is tracked via {@link #recordCompletion}. */
    public void recordOutcome(ResourceLocation quest, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            recordCompletion(quest);
        } else {
            outcomes.merge(outcomeKey(quest, outcome), 1, Integer::sum);
        }
    }

    public int outcomeCount(ResourceLocation quest, Outcome outcome) {
        if (outcome == Outcome.COMPLETED) {
            return completionCount(quest);
        }
        return outcomes.getOrDefault(outcomeKey(quest, outcome), 0);
    }

    public void copyFrom(QuestHistory other) {
        cooldownUntil.clear();
        cooldownUntil.putAll(other.cooldownUntil);
        completions.clear();
        completions.putAll(other.completions);
        outcomes.clear();
        outcomes.putAll(other.outcomes);
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
        return tag;
    }

    public void load(CompoundTag tag) {
        cooldownUntil.clear();
        completions.clear();
        outcomes.clear();
        CompoundTag cd = tag.getCompound("cooldowns");
        cd.getAllKeys().forEach(k -> cooldownUntil.put(k, cd.getLong(k)));
        CompoundTag done = tag.getCompound("completions");
        done.getAllKeys().forEach(k -> completions.put(k, done.getInt(k)));
        CompoundTag out = tag.getCompound("outcomes"); // absent on pre-chain saves -> empty compound
        out.getAllKeys().forEach(k -> outcomes.put(k, out.getInt(k)));
    }
}
