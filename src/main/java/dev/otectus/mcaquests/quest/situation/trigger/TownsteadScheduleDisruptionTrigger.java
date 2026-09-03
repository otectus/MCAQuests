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
import net.minecraft.util.ExtraCodecs;

/**
 * Opens when a village stops keeping to its own routine (spec §5.8).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_schedule_disruption",
 *   "minimum_observed": 5, "minimum_fraction": 0.50, "hold_ticks": 1200 }
 * }</pre>
 *
 * <p>Two guards keep this from crying wolf, and both are load-bearing. {@code minimum_observed} means
 * a claim about the village needs enough of the village to be visible — one distracted villager in
 * render distance is not a village in disarray. {@code hold_ticks} means the disruption has to
 * <em>persist</em>: villagers routinely spend a few seconds off schedule walking between jobs, and a
 * detector without a hold would fire on that constantly.
 *
 * <p>The recovery threshold is the third guard and lives in the detector: a fired disruption cannot
 * arm again until the off-schedule fraction has fallen well below the one that opened it, so a village
 * hovering on the boundary does not flicker a situation in and out of the quest list.
 */
public record TownsteadScheduleDisruptionTrigger(int minimumObserved, double minimumFraction,
                                                 int holdTicks) implements SituationTrigger {

    public static final MapCodec<TownsteadScheduleDisruptionTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_observed", 5)
                            .forGetter(TownsteadScheduleDisruptionTrigger::minimumObserved),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.05D, 1.0D), "minimum_fraction", 0.50D)
                            .forGetter(TownsteadScheduleDisruptionTrigger::minimumFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", 1200)
                            .forGetter(TownsteadScheduleDisruptionTrigger::holdTicks)
            ).apply(instance, TownsteadScheduleDisruptionTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_SCHEDULE_DISRUPTION;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_SCHEDULE_DISRUPTION;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        // The detector has already applied the hold and the hysteresis; what it reports is how many
        // residents it saw and what share were adrift, so a definition stricter than the detector's own
        // defaults still filters here.
        return signal.magnitude() >= minimumObserved && signal.fraction() >= minimumFraction;
    }
}
