package dev.otectus.mcaquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Optional datapack fields that <b>report</b> a malformed value instead of quietly substituting the
 * default.
 *
 * <p>DataFixerUpper's {@code Codec.lenientOptionalFieldOf} cannot tell "absent" from "present but
 * invalid" — both yield the default. For a datapack format that is the wrong trade. A pack author
 * who writes
 *
 * <pre>{@code "recipients": "phase_contributers"}</pre>
 *
 * would get {@code NOBODY} and no diagnostic, so the reward would silently go to nobody and the only
 * symptom would be players saying the project "did not pay". Making absence lenient and malformation
 * loud is the whole point.
 *
 * <p>PORT: DFU 8 (1.21) turned {@code optionalFieldOf} strict and moved the old forgiving behaviour
 * to {@code lenientOptionalFieldOf}. This mod's fields default to the forgiving form, so
 * {@code strictOptional} stays the deliberate opt-in wherever malformation should be loud.
 */
public final class StrictCodecs {

    private StrictCodecs() {
    }

    /** Absent → empty; present and valid → value; present and invalid → error naming the field. */
    public static <A> MapCodec<Optional<A>> strictOptional(Codec<A> codec, String name) {
        return new MapCodec<>() {

            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    return DataResult.success(Optional.empty());
                }
                return codec.parse(ops, value)
                        .map(Optional::of)
                        .mapError(error -> "field '" + name + "': " + error);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                return input.map(value -> prefix.add(name, codec.encodeStart(ops, value))).orElse(prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public String toString() {
                return "StrictOptional[" + name + "]";
            }
        };
    }

    /** Absent → {@code fallback}; present and valid → value; present and invalid → error. */
    public static <A> MapCodec<A> strictOptional(Codec<A> codec, String name, A fallback) {
        return strictOptional(codec, name).xmap(
                value -> value.orElse(fallback),
                value -> value == null || value.equals(fallback) ? Optional.empty() : Optional.of(value));
    }
}
