package dev.otectus.mcaquests.quest.dialogue;

import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Read-only registry of {@link VoicePool}s, swapped atomically by {@code VoiceLoader} on datapack
 * reload (mirrors {@code Titles} and {@code ProjectRegistry}), plus the selection that reads it.
 *
 * <p>Selection is <b>deterministic</b>, seeded through {@link QuestContext#stableRandom}. That is not
 * a detail: 1.4.3 fixed a reported bug where reopening a villager's menu re-voiced every offer, so
 * "the context and story of every quest changed while the quests stayed the same". A pool that rolled
 * afresh on each render would reintroduce exactly that, one layer down. The same villager, on the same
 * day, in the same mood, says the same thing.
 *
 * <p>Conditioned lines beat unconditioned ones. A pool that says something specific about a grumpy
 * villager at night should say it, rather than having its specificity diluted by the general lines
 * sitting beside it; the unconditioned lines are the floor for everyone else.
 */
public final class VoicePools {

    private static volatile Map<ResourceLocation, VoicePool> pools = Map.of();

    private VoicePools() {
    }

    public static void replaceAll(Map<ResourceLocation, VoicePool> loaded) {
        pools = Map.copyOf(loaded);
    }

    public static Set<ResourceLocation> ids() {
        return pools.keySet();
    }

    /** How many pools are loaded, for the reload log and the command's status output. */
    public static int size() {
        return pools.size();
    }

    /**
     * What this villager says in this situation, or empty when no pool has anything for them.
     *
     * <p>Empty is a perfectly good answer and every caller has a fallback: a quest's own line, or the
     * static status string the mod has always shown. Nothing here can leave a villager silent.
     */
    public static Optional<Component> pick(String state, QuestContext context) {
        List<VoiceLine> candidates = eligible(poolsFor(state), line -> line.matches(context));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        VoiceLine chosen = weightedPick(candidates, context.stableRandom("voice:" + state));
        return Optional.of(resolve(chosen.text()));
    }

    /** Every loaded pool for a state, highest priority first. */
    private static List<VoicePool> poolsFor(String state) {
        List<VoicePool> ordered = new ArrayList<>();
        for (Map.Entry<ResourceLocation, VoicePool> entry : pools.entrySet()) {
            if (entry.getValue().state().equals(state)) {
                ordered.add(entry.getValue());
            }
        }
        // Highest priority first, so a pack that wants to shadow the built-in voice can, without
        // having to delete a file it does not own.
        ordered.sort(Comparator.comparingInt(VoicePool::priority).reversed());
        return ordered;
    }

    /**
     * The lines a villager could say right now: every conditioned line that matches, or — when none
     * does — every unconditioned one.
     *
     * <p>Takes the pools and a predicate rather than a {@code QuestContext} so the selection rules can
     * be exercised without a server, in the spirit of {@code ScrollView} and {@code PanelGeometry}.
     * Package-private for {@code VoicePoolsTest}.
     */
    static List<VoiceLine> eligible(List<VoicePool> ordered, Predicate<VoiceLine> matches) {
        List<VoiceLine> conditioned = new ArrayList<>();
        List<VoiceLine> fallbacks = new ArrayList<>();
        for (VoicePool pool : ordered) {
            for (VoiceLine line : pool.lines()) {
                if (line.isFallback()) {
                    fallbacks.add(line);
                } else if (matches.test(line)) {
                    conditioned.add(line);
                }
            }
            // A higher-priority pool that has something to say ends the search: shadowing means
            // shadowing, not being averaged with what it replaced.
            if (!conditioned.isEmpty()) {
                return conditioned;
            }
        }
        return fallbacks;
    }

    static VoiceLine weightedPick(List<VoiceLine> candidates, Random random) {
        int total = 0;
        for (VoiceLine line : candidates) {
            total += Math.max(1, line.weight());
        }
        int roll = random.nextInt(total);
        for (VoiceLine line : candidates) {
            roll -= Math.max(1, line.weight());
            if (roll < 0) {
                return line;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** These lines carry no template placeholders, so the plain resolution is the right one. */
    private static Component resolve(QuestText text) {
        return text.resolve();
    }
}
