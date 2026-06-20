package dev.otectus.mcaquests.quest.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

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
    public static final Set<String> FAMILY_RELATIONS =
            Set.of("any", "parent", "child", "sibling", "grandparent");
    public static final Set<String> AGE_GROUPS =
            Set.of("baby", "toddler", "child", "teen", "adult");
    public static final Set<String> PERSONALITIES =
            Set.of("athletic", "confident", "friendly", "flirty", "witty", "shy", "gloomy",
                    "sensitive", "greedy", "odd", "lazy", "grumpy", "peppy");
    public static final Set<String> RELATED_RELATIONS =
            Set.of("spouse", "parent", "child", "sibling");
    public static final Set<String> RELATED_STATUSES =
            Set.of("alive", "nearby", "missing", "dead", "same_village");

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
