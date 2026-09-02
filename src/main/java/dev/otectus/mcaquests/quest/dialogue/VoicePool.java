package dev.otectus.mcaquests.quest.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;

/**
 * A pool of things villagers say in one situation, loaded from
 * {@code data/<ns>/mcaquests/dialogue/**.json}.
 *
 * <p>This exists because the alternative did not scale. Every quest may author its own line for every
 * lifecycle state, and 262 of them do for the six states a quest actually reaches — but the two states
 * that explain a villager having <em>nothing</em> to offer, {@code cooldown} and {@code locked}, were
 * authored by exactly none of them. The machinery to show those lines was written, tested and shipped;
 * there was simply no content, so every busy villager in the game said "I do not need anything right
 * now."
 *
 * <p>Writing 524 per-quest lines would have fixed it and taught the player nothing extra: "you brought
 * me wheat yesterday" is a better sentence than the flat refusal whether or not it is bespoke to the
 * wheat quest. A pool keyed by {@link VoiceLine#when()} says it once, in as many voices as the
 * condition language can distinguish, and covers every quest in the game — including quests from packs
 * that have never heard of this file.
 *
 * <p>A quest's own line always wins where it has one. This is the floor, not the ceiling.
 *
 * @param state    which situation these lines are for; see {@link #STATES}
 * @param priority higher pools are consulted first, so a pack can shadow the built-in voice without
 *                 having to delete it
 */
public record VoicePool(String state, int priority, List<VoiceLine> lines) {

    /** The villager has nothing to offer because you did their quest recently. */
    public static final String COOLDOWN = "cooldown";
    /** The villager has something, but you have not earned it yet. */
    public static final String LOCKED = "locked";
    /** The villager has nothing, and there is no more specific reason than that. */
    public static final String NO_QUESTS = "no_quests";
    /** Said in the menu header whenever the villager does have offers. */
    public static final String GREETING = "greeting";

    /**
     * Every state a pool may declare. Closed on purpose: a pool naming a state nothing reads is a file
     * that silently does nothing, which is exactly the class of bug
     * {@code DatapackFieldCoverageTest} exists to prevent.
     */
    public static final Set<String> STATES = Set.of(COOLDOWN, LOCKED, NO_QUESTS, GREETING);

    public static final Codec<VoicePool> CODEC = RecordCodecBuilder.<VoicePool>create(instance -> instance.group(
            Codec.STRING.fieldOf("state").forGetter(VoicePool::state),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(VoicePool::priority),
            VoiceLine.CODEC.listOf().fieldOf("lines").forGetter(VoicePool::lines)
    ).apply(instance, VoicePool::new)).flatXmap(VoicePool::validate, VoicePool::validate);

    private static DataResult<VoicePool> validate(VoicePool pool) {
        if (!STATES.contains(pool.state)) {
            return DataResult.error(() -> "unknown dialogue state '" + pool.state + "'; expected one of "
                    + STATES.stream().sorted().toList());
        }
        if (pool.lines.isEmpty()) {
            return DataResult.error(() -> "dialogue pool 'lines' must not be empty");
        }
        return DataResult.success(pool);
    }
}
