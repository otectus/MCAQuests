package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Locale;
import java.util.Optional;

/** Requires a time-of-day period and/or explicit tick range (spec section 13). */
public record TimeCondition(TimePeriod period, Optional<Integer> minTick, Optional<Integer> maxTick)
        implements QuestCondition {

    public enum TimePeriod {
        ANY, DAY, NIGHT, MORNING, EVENING;

        public boolean matches(long timeOfDay) {
            return switch (this) {
                case ANY -> true;
                case DAY -> timeOfDay < 12000L;
                case NIGHT -> timeOfDay >= 12000L;
                case MORNING -> timeOfDay < 2000L || timeOfDay >= 23000L;
                case EVENING -> timeOfDay >= 10000L && timeOfDay < 14000L;
            };
        }

        public static final Codec<TimePeriod> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown time period: " + name);
                    }
                },
                period -> DataResult.success(period.name().toLowerCase(Locale.ROOT)));
    }

    public static final Codec<TimeCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TimePeriod.CODEC.lenientOptionalFieldOf("period", TimePeriod.ANY).forGetter(TimeCondition::period),
            Codec.INT.lenientOptionalFieldOf("min").forGetter(TimeCondition::minTick),
            Codec.INT.lenientOptionalFieldOf("max").forGetter(TimeCondition::maxTick)
    ).apply(instance, TimeCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TIME;
    }

    @Override
    public boolean test(QuestContext context) {
        long timeOfDay = context.dayTime() % 24000L;
        boolean inRange = minTick.map(m -> timeOfDay >= m).orElse(true)
                && maxTick.map(m -> timeOfDay <= m).orElse(true);
        return period.matches(timeOfDay) && inRange;
    }
}
