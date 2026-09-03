package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Opens when a villager crosses a threshold in their own life (spec §5.8).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_life_transition", "transition": "senior", "from": false, "to": true }
 * }</pre>
 *
 * <p>Three axes, and the choice between them matters:
 *
 * <ul>
 *   <li>{@code canonical_stage} resolves the villager's current life stage through their root's
 *       {@code presentsAs} value, giving the semantic {@code child → adult} crossing. <b>Prefer this
 *       one.</b> Townstead roots may name their stages anything, so a custom root whose adult stage is
 *       called "butterfly" still produces a coming-of-age signal.</li>
 *   <li>{@code life_stage} is the raw stage id, for a pack that knows exactly which root it is writing
 *       for and wants that specific stage.</li>
 *   <li>{@code senior} is Townstead's own boolean.</li>
 * </ul>
 *
 * <p>The subject's UUID travels with the signal, so an offer can prefer and bind the villager it is
 * actually about rather than picking whichever neighbour happened to be nearest.
 *
 * <p>Nothing here grants, removes or ranks a life stage, and no content built on it may treat one as
 * better than another: a coming-of-age or a retirement is a celebration, not an assessment.
 */
public record TownsteadLifeTransitionTrigger(Axis transition, Optional<String> from,
                                             Optional<String> to) implements SituationTrigger {

    /** Which of a villager's life values is being watched. */
    public enum Axis {
        CANONICAL_STAGE("canonical_stage"),
        LIFE_STAGE("life_stage"),
        SENIOR("senior");

        private static final Map<String, Axis> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Axis::id, Function.identity()));

        static final Codec<Axis> CODEC = Codec.STRING.flatXmap(
                raw -> {
                    Axis axis = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                    return axis != null ? DataResult.success(axis) : DataResult.error(
                            () -> "Unknown life transition '" + raw + "'; expected one of " + BY_NAME.keySet());
                },
                axis -> DataResult.success(axis.id()));

        private final String id;

        Axis(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /**
     * {@code from} and {@code to} accept a boolean as well as a string, because {@code senior} is a
     * boolean axis and writing {@code "to": true} is what an author will reach for. Both are stored
     * lowercased, so {@code true} and {@code "true"} are the same filter.
     */
    private static final Codec<String> VALUE_CODEC = Codec.either(Codec.BOOL, Codec.STRING).xmap(
            either -> either.map(String::valueOf, value -> value.toLowerCase(Locale.ROOT)),
            com.mojang.datafixers.util.Either::right);

    public static final MapCodec<TownsteadLifeTransitionTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Axis.CODEC.fieldOf("transition").forGetter(TownsteadLifeTransitionTrigger::transition),
                    StrictCodecs.strictOptional(VALUE_CODEC, "from")
                            .forGetter(TownsteadLifeTransitionTrigger::from),
                    StrictCodecs.strictOptional(VALUE_CODEC, "to")
                            .forGetter(TownsteadLifeTransitionTrigger::to)
            ).apply(instance, TownsteadLifeTransitionTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_LIFE_TRANSITION;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_LIFE_TRANSITION;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.signalContext()
                .map(context -> context.matchesKind(transition.id())
                        && context.matchesFrom(from)
                        && context.matchesTo(to))
                .orElse(false);
    }
}
