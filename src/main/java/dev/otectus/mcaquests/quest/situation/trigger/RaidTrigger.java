package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/** Opens when a raid begins on/near the village (0.8.0). Carries no parameters. */
public record RaidTrigger() implements SituationTrigger {

    public static final MapCodec<RaidTrigger> CODEC = MapCodec.unit(RaidTrigger::new);

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.RAID;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.RAID;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return true;
    }
}
