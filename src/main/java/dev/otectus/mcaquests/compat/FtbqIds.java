package dev.otectus.mcaquests.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Pattern;

/**
 * Format validation for FTB Quests' 16-hex-digit code strings (spec §17). FTB ids are opaque
 * hex codes assigned by FTB Quests itself (e.g. {@code 1A2B3C4D5E6F7081}), optionally written with
 * a leading {@code '#'} (FTB's own convention in some UI/config surfaces). This class only checks
 * <em>shape</em> — whether a string looks like a well-formed FTB id — not whether it resolves to a
 * real quest/chapter/task in a loaded book; that existence cross-check is deliberately deferred to
 * {@code /mcaquests ftbq validate} (spec §17, M5.3), since a datapack may legitimately reference a
 * book that has not been built yet.
 *
 * <p>Zero FTB imports: this lives beside {@link FtbqBridge} in {@code compat} (not {@code compat.ftbq}),
 * so it classloads unconditionally and is reusable by both the always-registered
 * {@code mcaquests:ftbq_*} condition codecs (spec §17) and the future validate command (M5.3) without
 * either needing FTB on the classpath.
 */
public final class FtbqIds {

    /** Optional leading '#', then 1-16 hex digits — matches spec §17's exact regex, whole-string anchored. */
    private static final Pattern FORMAT = Pattern.compile("#?[0-9a-fA-F]{1,16}");

    private FtbqIds() {
    }

    /** Whether {@code id} is a well-formed FTB hex id (format only; existence is not checked here). */
    public static boolean isValidFormat(String id) {
        return id != null && FORMAT.matcher(id).matches();
    }

    /**
     * A {@link Codec} for a hex-id field: passes the raw string through unchanged (leading {@code '#'}
     * included, since {@code FtbqBridge} methods accept it as-is) but rejects malformed input at parse
     * time. {@code typeId} and {@code field} are folded into the error message so a lenient-mode load
     * failure (or a strict-mode {@code QuestValidationException}) names both, matching the convention
     * every other validated condition field in this codebase already follows (e.g.
     * {@code GiverDistanceFromVillageCondition}, {@code McaConditionCodecs.validated}).
     */
    public static Codec<String> hexIdCodec(String typeId, String field) {
        return Codec.STRING.flatXmap(
                raw -> isValidFormat(raw)
                        ? DataResult.success(raw)
                        : DataResult.error(() -> typeId + " '" + field
                                + "' must be an FTB hex id (regex #?[0-9a-fA-F]{1,16}), got: '" + raw + "'"),
                DataResult::success);
    }
}
