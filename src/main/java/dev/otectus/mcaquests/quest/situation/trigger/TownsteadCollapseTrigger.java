package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens the moment a villager collapses from exhaustion (Townstead spec 7.3). The detector fires only
 * on the false-to-true crossing, so a villager who stays down does not re-open this every second.
 */
public record TownsteadCollapseTrigger() implements SituationTrigger {

    public static final MapCodec<TownsteadCollapseTrigger> CODEC =
            MapCodec.unit(TownsteadCollapseTrigger::new);

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_COLLAPSE;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_COLLAPSE;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.villager().isPresent();
    }
}
