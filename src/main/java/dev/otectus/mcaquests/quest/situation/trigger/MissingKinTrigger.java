package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a relative of a villager goes missing (unloaded/not in the village) — see
 * {@code McaCompat.relativesWithStatus(..., "missing")} (0.8.0). {@code relation} narrows which kin
 * ({@code any}/{@code spouse}/{@code parent}/{@code child}/{@code sibling}).
 */
public record MissingKinTrigger(String relation) implements SituationTrigger {

    public static final Codec<MissingKinTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.lenientOptionalFieldOf("relation", "any").forGetter(MissingKinTrigger::relation)
    ).apply(instance, MissingKinTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.MISSING_KIN;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.MISSING_KIN;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return true;
    }
}
