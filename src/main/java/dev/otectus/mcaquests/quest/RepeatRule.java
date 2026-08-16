package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** How often a quest can be repeated (spec section 11). */
public record RepeatRule(RepeatType type, int cooldownTicks) {

    public enum RepeatType {
        ONCE,
        COOLDOWN,
        REPEATABLE;

        public static final Codec<RepeatType> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown repeat type: " + name);
                    }
                },
                type -> DataResult.success(type.name().toLowerCase(Locale.ROOT)));
    }

    public static final RepeatRule DEFAULT = new RepeatRule(RepeatType.COOLDOWN, 24000);

    public static final Codec<RepeatRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RepeatType.CODEC.optionalFieldOf("type", RepeatType.COOLDOWN).forGetter(RepeatRule::type),
            Codec.INT.optionalFieldOf("cooldown_ticks", 24000).forGetter(RepeatRule::cooldownTicks)
    ).apply(instance, RepeatRule::new));

    public boolean isRepeatable() {
        return type != RepeatType.ONCE;
    }
}
