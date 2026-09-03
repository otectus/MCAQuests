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
 * Opens at nightfall in the village (0.8.0). When {@code require_full_moon} is true it only fires on
 * full-moon nights (a higher-stakes "night watch").
 */
public record NightTrigger(boolean requireFullMoon) implements SituationTrigger {

    public static final MapCodec<NightTrigger> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("require_full_moon", false).forGetter(NightTrigger::requireFullMoon)
    ).apply(instance, NightTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.NIGHT;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.NIGHT;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return !requireFullMoon || signal.fullMoon();
    }
}
