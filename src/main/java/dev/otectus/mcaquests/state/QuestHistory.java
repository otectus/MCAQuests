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

    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private final Map<String, Integer> completions = new HashMap<>();

    private static String key(ResourceLocation quest, UUID villager) {
        return quest + "|" + villager;
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

    public void copyFrom(QuestHistory other) {
        cooldownUntil.clear();
        cooldownUntil.putAll(other.cooldownUntil);
        completions.clear();
        completions.putAll(other.completions);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag cd = new CompoundTag();
        cooldownUntil.forEach(cd::putLong);
        tag.put("cooldowns", cd);
        CompoundTag done = new CompoundTag();
        completions.forEach(done::putInt);
        tag.put("completions", done);
        return tag;
    }

    public void load(CompoundTag tag) {
        cooldownUntil.clear();
        completions.clear();
        CompoundTag cd = tag.getCompound("cooldowns");
        cd.getAllKeys().forEach(k -> cooldownUntil.put(k, cd.getLong(k)));
        CompoundTag done = tag.getCompound("completions");
        done.getAllKeys().forEach(k -> completions.put(k, done.getInt(k)));
    }
}
