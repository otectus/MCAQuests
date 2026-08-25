package dev.otectus.mcaquests.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One independently-bindable Townstead feature (Townstead spec §3.2).
 *
 * <p>The unit of failure for the whole integration: a binding miss disables exactly the capability
 * that needed it, makes the content declaring it ineligible, and produces one actionable diagnostic
 * — it never disables the bridge and never throws. Datapacks gate on these ids through
 * {@code mcaquests:townstead_available}, so the names are part of the public pack contract and must
 * not be renamed without a migration note.
 */
public enum TownsteadCapability {

    /** The villager snapshot: identity, life stage, age, personality, fertility, heritage. */
    READ_VILLAGER,
    /** World day, season, weekday and the active calendar profile. */
    READ_CALENDAR,
    /** The registered building at a position: type, size, bounds, owning village. */
    READ_BUILDING,
    /** A root (species/ancestry/lineage) definition and its life cycle. */
    READ_ROOT,
    /** A gene definition and its variants. */
    READ_GENE,
    /** Hunger, thirst, fatigue, collapse and the gated flag. */
    READ_NEEDS,
    /** Shift mode, template, current and planned activity. */
    READ_SCHEDULE,
    /** Profession id, tier and XP, plus the progression spec behind them. */
    READ_PROFESSION,
    /**
     * Village spirit totals, tier and classification. The one read that has to reach past
     * Townstead's public facade, so it is also the one most likely to report missing.
     */
    READ_SPIRIT,

    /** Writing hunger/thirst/fatigue back through Townstead's own setters. */
    MUTATE_NEEDS,
    /** Awarding profession XP through Townstead's progression, caps included. */
    AWARD_PROFESSION_XP,
    /** Learning and forgetting profession skills. */
    MUTATE_SKILLS,
    /** Playing a Townstead reaction on a quest, project or situation transition. */
    DISPATCH_REACTION;

    private static final Map<String, TownsteadCapability> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(c -> c.name().toLowerCase(Locale.ROOT), Function.identity()));

    /**
     * Accepts the constant name in either case, so a pack may write {@code READ_NEEDS} or
     * {@code read_needs}. An unknown id is a parse error naming the valid set rather than a silently
     * ungated definition -- a capability gate that quietly matched nothing would be worse than absent.
     */
    public static final Codec<TownsteadCapability> CODEC = Codec.STRING.flatXmap(
            raw -> {
                TownsteadCapability capability = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                return capability != null ? DataResult.success(capability) : DataResult.error(
                        () -> "Unknown Townstead capability '" + raw + "'; expected one of " + BY_NAME.keySet());
            },
            capability -> DataResult.success(capability.name()));
}
