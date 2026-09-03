package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when enough of a village is short of one need (Townstead spec 7.3).
 *
 * <pre>{@code { "type": "mcaquests:townstead_need", "need": "hunger", "minimum_fraction": 0.34 } }</pre>
 *
 * <p>The detector already applies hysteresis and only emits on the way in, so this sees a village
 * sliding into trouble rather than a village that is in trouble.
 */
public record TownsteadNeedTrigger(String need, float minimumFraction) implements SituationTrigger {

    public static final MapCodec<TownsteadNeedTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("need").forGetter(TownsteadNeedTrigger::need),
                    StrictCodecs.strictOptional(Codec.floatRange(0f, 1f), "minimum_fraction", 0.34f)
                            .forGetter(TownsteadNeedTrigger::minimumFraction)
            ).apply(instance, TownsteadNeedTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_NEED;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_NEED;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.fraction() >= minimumFraction
                && signal.signalContext().map(c -> c.matchesString(need)).orElse(false);
    }
}
