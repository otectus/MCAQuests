package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a villager in the scope dies (0.8.0). {@code relation} optionally narrows the trigger to
 * deaths of a player relative ({@code any}/{@code spouse}/{@code parent}/{@code child}/{@code sibling});
 * that player-relative filter is applied at offer eligibility, so detection fires for any village death.
 */
public record VillagerDeathTrigger(String relation) implements SituationTrigger {

    public static final Codec<VillagerDeathTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("relation", "any").forGetter(VillagerDeathTrigger::relation)
    ).apply(instance, VillagerDeathTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.VILLAGER_DEATH;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.VILLAGER_DEATH;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return true;
    }
}
