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

import java.util.Locale;
import java.util.Optional;

/**
 * Opens the moment a capital's throne falls vacant (1.6.0).
 *
 * <pre>{@code
 * { "type": "mcaquests:capital_interregnum", "sovereign": "villager" }
 * }</pre>
 *
 * <p>{@code sovereign} narrows which vacancy is news: {@code "villager"} for a villager sovereign who
 * died and left a succession to settle, {@code "player"} for a player sovereign whose capital may now
 * be waiting on someone who never logs in again, and {@code "any"} (the default) for both. The
 * distinction is carried on the signal itself rather than re-derived here, because by the time a
 * definition is tested Capitals may already have seated the heir.
 *
 * <p>The detector fires only on the crossing into vacancy, so a long interregnum does not re-open this
 * every poll, and the first observation after an install seeds the baseline silently.
 */
public record CapitalInterregnumTrigger(String sovereign) implements SituationTrigger {

    /** The value on the signal's {@code to} end for each accepted filter; {@code "any"} filters nothing. */
    private static final String VILLAGER_END = "vacant";
    private static final String PLAYER_END = "vacant_player";

    private static final Codec<String> SOVEREIGN_CODEC = Codec.STRING.comapFlatMap(value -> {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "any", "villager", "player" -> DataResult.success(normalized);
            default -> DataResult.error(() -> "Unknown sovereign filter: '" + value
                    + "' (expected any/villager/player)");
        };
    }, value -> value);

    public static final MapCodec<CapitalInterregnumTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(SOVEREIGN_CODEC, "sovereign", "any")
                            .forGetter(CapitalInterregnumTrigger::sovereign)
            ).apply(instance, CapitalInterregnumTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.CAPITAL_INTERREGNUM;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.CAPITAL_INTERREGNUM;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        if (sovereign == null) {
            return false;
        }
        return switch (sovereign.toLowerCase(Locale.ROOT)) {
            case "villager" -> signal.signalContext()
                    .map(context -> context.matchesTo(Optional.of(VILLAGER_END))).orElse(false);
            case "player" -> signal.signalContext()
                    .map(context -> context.matchesTo(Optional.of(PLAYER_END))).orElse(false);
            case "any" -> true;
            default -> false;
        };
    }
}
