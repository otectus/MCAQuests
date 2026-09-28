package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.CrimeBridge;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;
import java.util.Set;

/**
 * {@code mcaquests:crime_status} (1.7.1): what MCA: Crime currently records about the player.
 *
 * <p>Every field is optional and every present field must hold: {@code wanted} (a warrant is open),
 * {@code band} ({@code lawful}, {@code neutral} or {@code outlaw}), {@code jailed} (serving a sentence)
 * and {@code min_heat}. Without MCA: Crime the condition is never met — the same degradation the
 * incident conditions have without MCA: Reputation — so a quest gated on a criminal past never offers
 * itself where nobody can tell. An empty object is a parse error: a condition that constrains nothing
 * is a typo.
 */
public record CrimeStatusCondition(Optional<Boolean> wanted, Optional<String> band, Optional<Boolean> jailed,
                                   Optional<Long> minHeat) implements QuestCondition {

    public static final Set<String> BANDS = Set.of("lawful", "neutral", "outlaw");

    // Validate on the MapCodec and convert at the end: chaining flatXmap onto RecordCodecBuilder.create
    // would stop the fields inlining beside "type" under dispatch (see DispatchedCodecInlinesTest).
    public static final Codec<CrimeStatusCondition> CODEC = RecordCodecBuilder.<CrimeStatusCondition>mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("wanted").forGetter(CrimeStatusCondition::wanted),
            Codec.STRING.optionalFieldOf("band").forGetter(CrimeStatusCondition::band),
            Codec.BOOL.optionalFieldOf("jailed").forGetter(CrimeStatusCondition::jailed),
            Codec.LONG.optionalFieldOf("min_heat").forGetter(CrimeStatusCondition::minHeat)
    ).apply(instance, CrimeStatusCondition::new)).flatXmap(CrimeStatusCondition::validate, CrimeStatusCondition::validate).codec();

    private static DataResult<CrimeStatusCondition> validate(CrimeStatusCondition condition) {
        if (condition.wanted().isEmpty() && condition.band().isEmpty() && condition.jailed().isEmpty()
                && condition.minHeat().isEmpty()) {
            return DataResult.error(() -> "crime_status must name at least one of wanted, band, jailed, min_heat");
        }
        if (condition.band().isPresent() && !BANDS.contains(condition.band().get())) {
            return DataResult.error(() -> "crime_status band must be one of " + BANDS + ", was '"
                    + condition.band().get() + "'");
        }
        if (condition.minHeat().isPresent() && condition.minHeat().get() < 0L) {
            return DataResult.error(() -> "crime_status min_heat must be >= 0");
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CRIME_STATUS;
    }

    @Override
    public boolean test(QuestContext context) {
        Optional<CrimeBridge.CrimeQueries> live = CrimeBridge.queries();
        if (live.isEmpty() || context.player() == null) {
            return false;
        }
        CrimeBridge.CrimeQueries q = live.get();
        if (wanted.isPresent() && q.isWanted(context.player()) != wanted.get()) {
            return false;
        }
        if (band.isPresent() && !band.equals(q.band(context.player()))) {
            return false;
        }
        if (jailed.isPresent() && q.isJailed(context.player()) != jailed.get()) {
            return false;
        }
        return minHeat.isEmpty() || q.heat(context.player()) >= minHeat.get();
    }
}
