package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Optional;

/**
 * The {@code format_version} every quest, project and situation file may carry (1.7.0).
 *
 * <p>The field was documented as a "bump-safe version marker" from the first release and read by
 * nothing, so a file written for a later format would have been parsed as if it were this one, silently
 * losing or misreading whatever that later format added. It is now checked before a file is parsed: absent
 * or {@code 1} reads as always; anything newer than this build understands is refused with an error that
 * says so, and the definition is not loaded, exactly as any other file that cannot be read.
 */
public final class FormatVersion {

    /** The newest format this build reads. */
    public static final int SUPPORTED = 1;

    private FormatVersion() {
    }

    /** Why this build will not read the file, or empty when it will. */
    public static Optional<String> refusal(JsonElement json) {
        if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("format_version")) {
            return Optional.empty();
        }
        JsonElement value = json.getAsJsonObject().get("format_version");
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()
                || primitive.getAsDouble() != Math.rint(primitive.getAsDouble())) {
            return Optional.of("format_version must be a whole number, not " + value);
        }
        long version = primitive.getAsLong();
        if (version < 1) {
            return Optional.of("format_version " + version + " is not a format (the first is 1)");
        }
        if (version > SUPPORTED) {
            return Optional.of("this file is written for a newer MCA: Quests (format_version " + version
                    + "); this build reads format_version " + SUPPORTED + ". Update MCA: Quests to load it.");
        }
        return Optional.empty();
    }
}
