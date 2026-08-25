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
 * Opens when a villager rises a profession tier (Townstead spec 7.3).
 *
 * <pre>{@code { "type": "mcaquests:townstead_profession_tier", "profession": "minecraft:farmer",
 *              "minimum_tier": 3 } }</pre>
 *
 * <p>Only increases reach here, so losing a tier is never mistaken for reaching one.
 */
public record TownsteadProfessionTierTrigger(Optional<String> profession, int minimumTier)
        implements SituationTrigger {

    public static final Codec<TownsteadProfessionTierTrigger> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "profession")
                            .forGetter(TownsteadProfessionTierTrigger::profession),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_tier", 1)
                            .forGetter(TownsteadProfessionTierTrigger::minimumTier)
            ).apply(instance, TownsteadProfessionTierTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_PROFESSION_TIER;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_PROFESSION_TIER;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        if (signal.magnitude() < minimumTier) {
            return false;
        }
        return profession.isEmpty()
                || signal.signalContext().map(c -> c.matchesString(profession.get())).orElse(false);
    }
}
