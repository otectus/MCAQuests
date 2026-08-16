package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The reputation/hearts deltas applied when a situation closes (0.8.0). Each branch is a
 * {@link Outcome} that defaults to no change, so a datapack only declares the branches it cares about.
 * Reputation deltas route through {@code ReputationService} (wired in a later phase); hearts go to the
 * involved villager(s).
 *
 * <ul>
 *   <li>{@code success} — a player resolved the situation (its quest was completed).</li>
 *   <li>{@code failure} — the deadline expired or the condition resolved against the village.</li>
 *   <li>{@code cleared} — the condition lifted on its own (e.g. the raid ended) — usually neutral.</li>
 * </ul>
 */
public record SituationOutcomes(Outcome success, Outcome failure, Outcome cleared) {

    public static final SituationOutcomes NONE = new SituationOutcomes(Outcome.NONE, Outcome.NONE, Outcome.NONE);

    public static final Codec<SituationOutcomes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Outcome.CODEC.lenientOptionalFieldOf("success", Outcome.NONE).forGetter(SituationOutcomes::success),
            Outcome.CODEC.lenientOptionalFieldOf("failure", Outcome.NONE).forGetter(SituationOutcomes::failure),
            Outcome.CODEC.lenientOptionalFieldOf("cleared", Outcome.NONE).forGetter(SituationOutcomes::cleared)
    ).apply(instance, SituationOutcomes::new));

    /** A single outcome branch: a village-reputation delta and a villager-hearts delta. */
    public record Outcome(int reputation, int hearts) {

        public static final Outcome NONE = new Outcome(0, 0);

        public static final Codec<Outcome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.lenientOptionalFieldOf("reputation", 0).forGetter(Outcome::reputation),
                Codec.INT.lenientOptionalFieldOf("hearts", 0).forGetter(Outcome::hearts)
        ).apply(instance, Outcome::new));
    }
}
