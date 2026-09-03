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

import java.util.Optional;

/**
 * Opens when a village gains a spirit tier or settles into a different identity (Townstead spec 7.3).
 *
 * <pre>{@code { "type": "mcaquests:townstead_spirit", "spirit": "nautical", "minimum_tier": 2 } }</pre>
 *
 * <p>1.4.1 adds the <b>classification</b> filters, which ask a different question: not "which spirit"
 * but "what kind of place has this become". Townstead reduces a village's spread of spirits to
 * {@code settlement}, a single named identity, {@code blend} or {@code mixed}, and a village crossing
 * from one of those into another is a story in itself — a settlement acquiring a character, or two
 * characters learning to share a town.
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_spirit", "to_classification": "blend", "minimum_tier": 1,
 *   "transition_only": true }
 * }</pre>
 *
 * <p>{@code transition_only} narrows the trigger to a genuine <em>crossing</em>. Without it a
 * definition also opens on a tier rising within an unchanged classification, which is the historical
 * behaviour and remains the default so no existing pack changes meaning.
 */
public record TownsteadSpiritTrigger(Optional<String> spirit, int minimumTier,
                                     Optional<String> fromClassification,
                                     Optional<String> toClassification,
                                     boolean transitionOnly) implements SituationTrigger {

    public static final MapCodec<TownsteadSpiritTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritTrigger::spirit),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_tier", 1)
                            .forGetter(TownsteadSpiritTrigger::minimumTier),
                    StrictCodecs.strictOptional(Codec.STRING, "from_classification")
                            .forGetter(TownsteadSpiritTrigger::fromClassification),
                    StrictCodecs.strictOptional(Codec.STRING, "to_classification")
                            .forGetter(TownsteadSpiritTrigger::toClassification),
                    StrictCodecs.strictOptional(Codec.BOOL, "transition_only", false)
                            .forGetter(TownsteadSpiritTrigger::transitionOnly)
            ).apply(instance, TownsteadSpiritTrigger::new));

    /** The pre-1.4.1 shape, for callers and tests that predate the classification filters. */
    public TownsteadSpiritTrigger(Optional<String> spirit, int minimumTier) {
        this(spirit, minimumTier, Optional.empty(), Optional.empty(), false);
    }

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
        boolean wantsClassification = fromClassification.isPresent() || toClassification.isPresent()
                || transitionOnly;
        if (wantsClassification) {
            // A classification filter needs a signal that carries one. A tier-only signal fails rather
            // than matching, so a definition asking about "becoming a blend" never opens on a village
            // that merely built another dock.
            return signal.signalContext()
                    .map(context -> context.matchesFrom(fromClassification)
                            && context.matchesTo(toClassification)
                            && (!transitionOnly || context.to().isPresent()))
                    .orElse(false);
        }
        return spirit.isEmpty()
                || signal.signalContext().map(c -> c.matchesString(spirit.get())).orElse(false);
    }
}
