package dev.otectus.mcaquests.quest.situation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The extra facts a Townstead signal carries that the original six did not need
 * (Townstead spec §7.3).
 *
 * <p>{@link TriggerSignal}'s existing fields answer "which village, which villager, how much". A
 * Townstead signal often has to answer "which <em>thing</em>": which need is in crisis, which
 * profession went up a tier, which spirit a village has settled into. Rather than widen the record
 * with five more mostly-null columns, that lives here and the signal carries at most one of these.
 *
 * <p>Every field is optional, and a signal without a context is exactly as valid as one with — which is
 * what lets the six original factories keep working untouched.
 */
public record SignalContext(@Nullable ResourceLocation key, @Nullable String stringValue,
                            @Nullable Double numericValue, @Nullable Integer oldTier,
                            @Nullable Integer newTier,
                            @Nullable String fromLabel, @Nullable String toLabel) {

    /** The pre-1.4.1 shape, for the contexts that carry no named transition. */
    public SignalContext(@Nullable ResourceLocation key, @Nullable String stringValue,
                         @Nullable Double numericValue, @Nullable Integer oldTier,
                         @Nullable Integer newTier) {
        this(key, stringValue, numericValue, oldTier, newTier, null, null);
    }

    /** A need or building kind, plus the reading that put it in crisis. */
    public static SignalContext of(String kind, double value) {
        return new SignalContext(null, kind, value, null, null);
    }

    /** A tier that has risen, carrying both ends so a definition can gate on how far it jumped. */
    public static SignalContext tierChange(String kind, int oldTier, int newTier) {
        return new SignalContext(null, kind, null, oldTier, newTier);
    }

    /** An identity a village has taken on: a spirit id, a building type, a profession. */
    public static SignalContext identity(@Nullable ResourceLocation key, String value) {
        return new SignalContext(key, value, null, null, null);
    }

    /**
     * A named crossing: what changed ({@code kind}), and the values either side of it.
     *
     * <p>Both ends are carried because a definition may want either — "into winter" cares only about
     * the destination, while "child to adult" is only meaningful as a pair. Everything here is a plain
     * lowercase string taken from the loaded profile or root definition, so a custom calendar with two
     * seasons or a root whose adult stage is called something else still produces a usable signal.
     */
    public static SignalContext transition(String kind, String from, String to) {
        return new SignalContext(null, kind, null, null, null, from, to);
    }

    /**
     * A village spirit reading that carries both the tier it moved through and the classification
     * either side of it, so one signal can answer "which spirit", "how far" and "what kind of place
     * has this become" without three separate signals to keep in step.
     */
    public static SignalContext spiritChange(String identity, int oldTier, int newTier,
                                             @Nullable String fromClassification,
                                             @Nullable String toClassification) {
        return new SignalContext(null, identity, null, oldTier, newTier,
                fromClassification, toClassification);
    }

    /** The value before the crossing, when this context describes one. */
    public Optional<String> from() {
        return Optional.ofNullable(fromLabel);
    }

    /** The value after the crossing, when this context describes one. */
    public Optional<String> to() {
        return Optional.ofNullable(toLabel);
    }

    /** True when this context is about {@code kind} -- the period, axis or need that changed. */
    public boolean matchesKind(String kind) {
        return matchesString(kind);
    }

    /** True when {@code wanted} is absent (no filter) or matches the value before the crossing. */
    public boolean matchesFrom(Optional<String> wanted) {
        return matchesEnd(wanted, fromLabel);
    }

    /** True when {@code wanted} is absent (no filter) or matches the value after the crossing. */
    public boolean matchesTo(Optional<String> wanted) {
        return matchesEnd(wanted, toLabel);
    }

    /**
     * An absent filter matches anything; a present one needs a value to compare against, so a signal
     * that carries no such end fails rather than matching a filter it cannot answer.
     */
    private static boolean matchesEnd(Optional<String> wanted, @Nullable String actual) {
        return wanted.isEmpty() || (actual != null && actual.equalsIgnoreCase(wanted.get()));
    }

    public Optional<String> string() {
        return Optional.ofNullable(stringValue);
    }

    public Optional<ResourceLocation> id() {
        return Optional.ofNullable(key);
    }

    public double number(double fallback) {
        return numericValue == null ? fallback : numericValue;
    }

    public OptionalInt tierBefore() {
        return oldTier == null ? OptionalInt.empty() : OptionalInt.of(oldTier);
    }

    public OptionalInt tierAfter() {
        return newTier == null ? OptionalInt.empty() : OptionalInt.of(newTier);
    }

    /** How far a tier moved, or {@code 0} when this context is not about a tier at all. */
    public int tierJump() {
        return oldTier == null || newTier == null ? 0 : newTier - oldTier;
    }

    /** True when {@code candidate} matches this context's string, case-insensitively. */
    public boolean matchesString(String candidate) {
        return stringValue != null && stringValue.equalsIgnoreCase(candidate);
    }
}
