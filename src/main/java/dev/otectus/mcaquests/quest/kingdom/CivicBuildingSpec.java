package dev.otectus.mcaquests.quest.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;

import java.util.Locale;
import java.util.Optional;

/** Data-driven binding to the civic building occupied by the giver or player at acceptance. */
public record CivicBuildingSpec(Target target, Recovery recovery, Optional<String> failureReason) {
    public static final Codec<CivicBuildingSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.strictOptional(Target.CODEC, "target", Target.GIVER).forGetter(CivicBuildingSpec::target),
            StrictCodecs.strictOptional(Recovery.CODEC, "recovery", Recovery.WAIT).forGetter(CivicBuildingSpec::recovery),
            StrictCodecs.strictOptional(Codec.STRING, "failure_reason").forGetter(CivicBuildingSpec::failureReason)
    ).apply(instance, CivicBuildingSpec::new));

    public CivicBuildingSpec {
        failureReason = failureReason == null ? Optional.empty() : failureReason.map(String::strip)
                .filter(value -> !value.isEmpty());
        if (recovery == Recovery.FAIL_WITH_REASON && failureReason.isEmpty()) {
            throw new IllegalArgumentException("civic_building.failure_reason is required for fail_with_reason");
        }
    }

    public enum Target {
        GIVER, PLAYER;
        static final Codec<Target> CODEC = enumCodec(Target.class, "civic building target");
    }

    public enum Recovery {
        WAIT, REBIND_SAME_FAMILY, FAIL_WITH_REASON;
        static final Codec<Recovery> CODEC = enumCodec(Recovery.class, "civic building recovery policy");
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, String label) {
        return Codec.STRING.flatXmap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown " + label + ": " + value);
            }
        }, value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }
}
