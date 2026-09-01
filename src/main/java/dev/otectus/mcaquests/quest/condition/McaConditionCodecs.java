package dev.otectus.mcaquests.quest.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.target.VillagerTarget;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared validating codecs + canonical accepted-value sets for the v0.3.0 MCA-aware conditions
 * (Phase 1 §4). Unknown enum-like values are rejected at parse time, so in lenient mode the loader
 * skips the quest with a clear logged error and in strict mode (config {@code strictJsonValidation})
 * it is a hard error — mirroring {@code TimeCondition.TimePeriod.CODEC}.
 */
public final class McaConditionCodecs {

    public static final Set<String> RELATIONSHIP_STATES =
            Set.of("single", "promised", "engaged", "married_to_villager", "married_to_player", "widow");
    /**
     * Relations {@code is_family_member} may gate on. This asks how the villager is related <em>to the
     * player</em>, which runs in the opposite direction to {@link #RELATED_RELATIONS} — the giver being
     * the player's "child" means the player is one of the giver's parents.
     *
     * <p>{@code spouse} used to be missing here purely because {@code McaCompat.isFamilyOfPlayer} had no
     * branch for it, so a pack author writing {@code "relation": "spouse"} got a hard load error from this
     * condition and silent success from {@code related_villager_status}. The branch exists now (marriage
     * is a relationship edge in MCA, not a family-tree one), so the two vocabularies agree.
     */
    public static final Set<String> FAMILY_RELATIONS =
            Set.of("any", "spouse", "parent", "child", "sibling", "grandparent");
    public static final Set<String> AGE_GROUPS =
            Set.of("baby", "toddler", "child", "teen", "adult");
    public static final Set<String> PERSONALITIES =
            Set.of("athletic", "confident", "friendly", "flirty", "witty", "shy", "gloomy",
                    "sensitive", "greedy", "odd", "lazy", "grumpy", "peppy");
    /**
     * Relations {@code related_villager_status} may gate on.
     *
     * <p><b>Is</b> {@code VillagerTarget.RELATIONS} rather than a copy of it, so a quest can gate on
     * exactly the relation its objective then selects and the two can never drift. The javadoc used to
     * claim they mirrored each other while they were two separately-maintained literals.
     */
    public static final Set<String> RELATED_RELATIONS = VillagerTarget.RELATIONS;

    /**
     * Statuses {@code related_villager_status} may gate on — the same seven a villager target's
     * {@code require} accepts, for the same reason: a gate has to be able to ask precisely the question
     * the objective will later ask.
     *
     * <p>{@code reachable} and {@code any_known} are the additions. {@code reachable} is what a quest
     * should almost always gate on ("a real person who can be found"), and it is the default a family
     * target requires; {@code any_known} is the old loose behaviour, kept so a pack that genuinely wants
     * "anyone in the family tree, dead or invented" can still say so — explicitly, rather than by
     * accident.
     */
    public static final Set<String> RELATED_STATUSES = RelativeCandidate.STATUSES;

    private McaConditionCodecs() {
    }

    /** A lowercased string validated against {@code allowed}; rejects anything else at parse time. */
    public static Codec<String> validated(String what, Set<String> allowed) {
        return Codec.STRING.flatXmap(
                raw -> {
                    String value = raw.toLowerCase(Locale.ROOT);
                    return allowed.contains(value)
                            ? DataResult.success(value)
                            : DataResult.error(() -> "Unknown " + what + ": '" + raw + "' (expected one of " + allowed + ')');
                },
                DataResult::success);
    }

    /** A non-empty list of values validated against {@code allowed}. */
    public static Codec<List<String>> validatedNonEmptyList(String what, Set<String> allowed) {
        return validated(what, allowed).listOf().flatXmap(
                list -> list.isEmpty()
                        ? DataResult.error(() -> what + " list must not be empty")
                        : DataResult.success(list),
                DataResult::success);
    }

    /** A non-empty list of free-form lowercased strings (used where the value set is data-driven). */
    public static Codec<List<String>> lowercaseNonEmptyList(String what) {
        return Codec.STRING.xmap(s -> s.toLowerCase(Locale.ROOT), s -> s).listOf().flatXmap(
                list -> list.isEmpty()
                        ? DataResult.error(() -> what + " list must not be empty")
                        : DataResult.success(list),
                DataResult::success);
    }
}
