package dev.otectus.mcaquests.quest.objective;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The frozen starting point a Townstead delta objective measures against (Townstead spec §5.2),
 * stored in {@link ObjectiveProgress#extra()}.
 *
 * <p><b>A baseline is written once and never rewritten.</b> That is the whole point of it. "Feed this
 * villager back up to full" means nothing without a record of how hungry they were when you were
 * asked, and re-reading that number on the next poll — or worse, after a restart — would silently turn
 * the quest into "keep them fed for one tick" and hand out the reward for nothing.
 *
 * <p>So every write goes through {@link #freeze}, which refuses to overwrite, and the schema carries
 * enough to prove it is still the <em>same</em> question: the source and path it was taken from, and
 * the villager it was taken about. If any of those stop matching, the objective reports missing rather
 * than quietly measuring against a stranger.
 *
 * <p>Everything is written with the {@code townstead_} prefix into the shared scratch tag, and only
 * when non-default, so a quest that never froze one serialises exactly as it always did.
 */
public final class TownsteadBaseline {

    /** Bumped only for a change that existing saves cannot be read under. */
    public static final int SCHEMA = 1;

    private static final String K_SCHEMA = "townstead_schema";
    private static final String K_SOURCE = "townstead_source";
    private static final String K_PATH = "townstead_path";
    private static final String K_TARGET = "townstead_target_uuid";
    private static final String K_TYPE = "townstead_baseline_type";
    private static final String K_NUMBER = "townstead_baseline_number";
    private static final String K_STRING = "townstead_baseline_string";
    private static final String K_TICK = "townstead_baseline_tick";
    private static final String K_ABSENT_SINCE = "townstead_absent_since";

    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_STRING = "string";
    /** The value could not be read when the baseline was taken, which is itself worth remembering. */
    private static final String TYPE_ABSENT = "absent";

    private TownsteadBaseline() {
    }

    /** True once a baseline has been taken, whatever it contains. */
    public static boolean isFrozen(ObjectiveProgress progress) {
        return progress.extra().contains(K_SCHEMA, Tag.TAG_INT);
    }

    /**
     * Records the starting value, the first time only. Later calls are no-ops, so a caller may invoke
     * this on every poll without having to remember whether it has already run.
     *
     * @param value the observed value, or {@code null} when it could not be read
     * @return true if this call is the one that froze it
     */
    public static boolean freeze(ObjectiveProgress progress, String source, String path,
                                 @Nullable UUID target, @Nullable Object value, long gameTime) {
        if (isFrozen(progress)) {
            return false;
        }
        CompoundTag extra = progress.extra();
        extra.putInt(K_SCHEMA, SCHEMA);
        extra.putString(K_SOURCE, source);
        extra.putString(K_PATH, path);
        extra.putLong(K_TICK, gameTime);
        if (target != null) {
            extra.putUUID(K_TARGET, target);
        }
        if (value instanceof Number number) {
            extra.putString(K_TYPE, TYPE_NUMBER);
            extra.putDouble(K_NUMBER, number.doubleValue());
        } else if (value != null) {
            extra.putString(K_TYPE, TYPE_STRING);
            extra.putString(K_STRING, String.valueOf(value));
        } else {
            extra.putString(K_TYPE, TYPE_ABSENT);
        }
        return true;
    }

    /**
     * True when a frozen baseline was taken from this same question. A mismatch means the datapack was
     * edited under a live quest, and the honest answer is to stop rather than to compare a hunger
     * baseline against a fatigue reading.
     */
    public static boolean matches(ObjectiveProgress progress, String source, String path) {
        CompoundTag extra = progress.extra();
        return extra.getString(K_SOURCE).equals(source) && extra.getString(K_PATH).equals(path);
    }

    public static OptionalDouble number(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return TYPE_NUMBER.equals(extra.getString(K_TYPE))
                ? OptionalDouble.of(extra.getDouble(K_NUMBER))
                : OptionalDouble.empty();
    }

    public static Optional<String> text(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return TYPE_STRING.equals(extra.getString(K_TYPE))
                ? Optional.of(extra.getString(K_STRING))
                : Optional.empty();
    }

    /** The villager the baseline is about, when it was taken about one. */
    public static Optional<UUID> target(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return extra.hasUUID(K_TARGET) ? Optional.of(extra.getUUID(K_TARGET)) : Optional.empty();
    }

    /** The game time the baseline was taken, for diagnostics and for "how long has this been going". */
    public static OptionalLong tick(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return isFrozen(progress) ? OptionalLong.of(extra.getLong(K_TICK)) : OptionalLong.empty();
    }

    /**
     * Notes when the objective first found itself unable to read Townstead, and leaves it noted. Used by
     * diagnostics to answer "since when", and deliberately not used to expire anything: a suspended
     * quest waits indefinitely rather than being cleaned up behind the player's back.
     */
    public static void markAbsent(ObjectiveProgress progress, long gameTime) {
        CompoundTag extra = progress.extra();
        if (!extra.contains(K_ABSENT_SINCE, Tag.TAG_LONG)) {
            extra.putLong(K_ABSENT_SINCE, gameTime);
        }
    }

    /** Clears the absence marker once readings are available again. */
    public static void clearAbsent(ObjectiveProgress progress) {
        progress.extra().remove(K_ABSENT_SINCE);
    }

    public static OptionalLong absentSince(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return extra.contains(K_ABSENT_SINCE, Tag.TAG_LONG)
                ? OptionalLong.of(extra.getLong(K_ABSENT_SINCE))
                : OptionalLong.empty();
    }
}
