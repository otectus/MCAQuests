package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a village's banked food drops to {@code threshold} edible items or fewer (a famine signal;
 * the count comes from MCA's village storage — see {@code McaCompat.getVillageFoodCount}).
 */
public record LowFoodTrigger(int threshold) implements SituationTrigger {

    public static final MapCodec<LowFoodTrigger> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("threshold", 16).forGetter(LowFoodTrigger::threshold)
    ).apply(instance, LowFoodTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.LOW_FOOD;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.LOW_FOOD;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.magnitude() <= threshold;
    }
}
