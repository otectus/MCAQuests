package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadPeriod;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

import java.util.Optional;

/**
 * Opens when the Townstead calendar turns over (spec §5.8).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_calendar_transition", "transition": "season", "to": "winter" }
 * }</pre>
 *
 * <p>{@code transition} is {@code week}, {@code season} or {@code year}; {@code from} and {@code to}
 * are optional filters on the values either side of the crossing, and with neither the trigger simply
 * fires on every turn of that period.
 *
 * <p><b>Nothing here assumes four seasons or a fixed year length.</b> The values come from the loaded
 * calendar profile, so a datapack that defines two seasons or a thirty-day year works without a code
 * change — and a definition naming a season the profile does not contain is caught by the validator
 * rather than waiting silently forever for a winter that will never come.
 *
 * <p>The first observation of a calendar seeds the baseline and never fires, and so does a change of
 * calendar profile: switching profiles is not a season changing, and treating it as one would greet a
 * returning player with a queue of festivals for a year that never happened.
 */
public record TownsteadCalendarTransitionTrigger(TownsteadPeriod transition, Optional<String> from,
                                                 Optional<String> to) implements SituationTrigger {

    public static final Codec<TownsteadCalendarTransitionTrigger> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    TownsteadPeriod.TRANSITION_CODEC.fieldOf("transition")
                            .forGetter(TownsteadCalendarTransitionTrigger::transition),
                    StrictCodecs.strictOptional(Codec.STRING, "from")
                            .forGetter(TownsteadCalendarTransitionTrigger::from),
                    StrictCodecs.strictOptional(Codec.STRING, "to")
                            .forGetter(TownsteadCalendarTransitionTrigger::to)
            ).apply(instance, TownsteadCalendarTransitionTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_CALENDAR_TRANSITION;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_CALENDAR_TRANSITION;
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
