package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/** Requires clear / rain / thunder weather (spec section 13). */
public record WeatherCondition(Weather weather) implements QuestCondition {

    public enum Weather {
        ANY, CLEAR, RAIN, THUNDER;

        public static final Codec<Weather> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown weather: " + name);
                    }
                },
                weather -> DataResult.success(weather.name().toLowerCase(Locale.ROOT)));
    }

    public static final Codec<WeatherCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Weather.CODEC.fieldOf("weather").forGetter(WeatherCondition::weather)
    ).apply(instance, WeatherCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.WEATHER;
    }

    @Override
    public boolean test(QuestContext context) {
        ServerLevel level = context.level();
        return switch (weather) {
            case ANY -> true;
            case CLEAR -> !level.isRaining();
            case RAIN -> level.isRaining() && !level.isThundering();
            case THUNDER -> level.isThundering();
        };
    }
}
