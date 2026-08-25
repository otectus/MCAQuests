package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

import java.util.Optional;

/**
 * Opens when a village gains a spirit tier or settles into a different identity (Townstead spec 7.3).
 *
 * <pre>{@code { "type": "mcaquests:townstead_spirit", "spirit": "nautical", "minimum_tier": 2 } }</pre>
 */
public record TownsteadSpiritTrigger(Optional<String> spirit, int minimumTier) implements SituationTrigger {

    public static final Codec<TownsteadSpiritTrigger> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritTrigger::spirit),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_tier", 1)
                            .forGetter(TownsteadSpiritTrigger::minimumTier)
            ).apply(instance, TownsteadSpiritTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_SPIRIT;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_SPIRIT;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        if (signal.magnitude() < minimumTier) {
            return false;
        }
        return spirit.isEmpty()
                || signal.signalContext().map(c -> c.matchesString(spirit.get())).orElse(false);
    }
}
