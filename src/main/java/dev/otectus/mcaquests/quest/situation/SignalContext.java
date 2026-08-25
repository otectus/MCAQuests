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
                            @Nullable Integer newTier) {

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
