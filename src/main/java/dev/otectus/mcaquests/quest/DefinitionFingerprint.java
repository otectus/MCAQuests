package dev.otectus.mcaquests.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A short, stable fingerprint of one piece of authored content — an objective, a reward — so a saved
 * record can tell whether the definition it was made against has changed since (1.7.0).
 *
 * <p>The value is encoded back through its own codec and written as canonical JSON (object keys sorted,
 * no whitespace) before hashing, so two files that differ only in key order or formatting agree, and a
 * change to anything the codec reads does not. Content whose codec cannot encode has no fingerprint;
 * callers treat that as "cannot tell", never as "unchanged".
 */
public final class DefinitionFingerprint {

    /** Hex characters kept: enough to make an accidental collision irrelevant, short enough to save. */
    static final int LENGTH = 16;

    private DefinitionFingerprint() {
    }

    public static <T> Optional<String> of(Codec<T> codec, T value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return codec.encodeStart(JsonOps.INSTANCE, value).result()
                    .map(DefinitionFingerprint::canonical)
                    .map(DefinitionFingerprint::hash);
        } catch (RuntimeException unencodable) {
            return Optional.empty();
        }
    }

    /** Fingerprints of a list, in order; an entry that cannot be fingerprinted is the empty string. */
    public static <T> List<String> ofEach(Codec<T> codec, List<? extends T> values) {
        List<String> out = new ArrayList<>(values.size());
        for (T value : values) {
            out.add(of(codec, value).orElse(""));
        }
        return out;
    }

    /** Key-sorted, whitespace-free JSON. */
    static String canonical(JsonElement element) {
        StringBuilder out = new StringBuilder();
        write(element, out);
        return out.toString();
    }

    private static void write(JsonElement element, StringBuilder out) {
        if (element == null || element.isJsonNull()) {
            out.append("null");
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            object.entrySet().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            out.append('{');
            boolean first = true;
            for (var entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(new com.google.gson.JsonPrimitive(entry.getKey())).append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(array.get(i), out);
            }
            out.append(']');
        } else {
            out.append(element);
        }
    }

    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
