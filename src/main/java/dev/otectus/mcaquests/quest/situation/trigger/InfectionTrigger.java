package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a villager's zombie-infection progress reaches {@code min_progress} (0..1, default 0)
 * — see {@code McaCompat.getInfectionProgress} (0.8.0).
 */
public record InfectionTrigger(float minProgress) implements SituationTrigger {

    public static final Codec<InfectionTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0f, 1.0f).optionalFieldOf("min_progress", 0.0f).forGetter(InfectionTrigger::minProgress)
    ).apply(instance, InfectionTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.INFECTION;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.INFECTION;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.fraction() >= minProgress;
    }
}
