package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a villager in the scope dies (0.8.0).
 *
 * <p>{@code relation} narrows <em>who may raise it</em>: with {@code "relation": "child"}, only a villager
 * who lost a child offers the situation. Detection still fires for any village death, because the village
 * has lost someone either way; the relation decides whose grief the quest is about.
 *
 * <p>Applied at offer eligibility rather than here, because it is a question about a candidate giver and a
 * signal has none. Until 1.4.3 the javadoc claimed exactly that and there was no such call site anywhere —
 * the field was parsed and ignored. It is now applied in {@code DynamicOfferSource}.
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

    /** Always matches: every death opens the situation, and {@link #relation} decides who may take it. */
    @Override
    public boolean matches(TriggerSignal signal) {
        return true;
    }
}
