package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadPaths;
import dev.otectus.mcaquests.compat.TownsteadQuery;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Complete when a Townstead value has moved far enough from where it started (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_change",
 *   "target": "giver",
 *   "source": "villager",
 *   "path": "needs.hunger",
 *   "direction": "increase",
 *   "amount": 45,
 *   "minimum_final": 70
 * }
 * }</pre>
 *
 * <p>"Feed this villager back up" only means something relative to how hungry they were when you were
 * asked, so the starting value is frozen at accept time by {@link TownsteadBaseline} and never
 * re-read. Without that, a restart would re-baseline against the current value and the quest would
 * complete for free.
 *
 * <p>{@code minimum_final} and {@code maximum_final} are absolute floors and ceilings on the end
 * state, independent of {@code direction}. They exist because a delta alone can be satisfied
 * pathologically: a villager who starves to 5 and is fed to 50 has gained 45 hunger without being
 * anything like well fed.
 */
public record TownsteadChangeObjective(TownsteadQuery query, Direction direction, double amount,
                                       Optional<Double> minimumFinal, Optional<Double> maximumFinal,
                                       boolean baselineOnAccept)
        implements PollingObjective, TownsteadObjective {

    /** {@code progress.extra()} flag: the end-state floors and ceilings were met at the last reading. */
    private static final String K_FINAL_OK = "townstead_final_ok";

    public enum Direction {
        INCREASE("increase"),
        DECREASE("decrease");

        private static final Map<String, Direction> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Direction::id, Function.identity()));

        static final Codec<Direction> CODEC = Codec.STRING.flatXmap(
                raw -> {
                    Direction direction = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                    return direction != null ? DataResult.success(direction) : DataResult.error(
                            () -> "Unknown Townstead direction '" + raw + "'; expected increase or decrease");
                },
                direction -> DataResult.success(direction.id()));

        private final String id;

        Direction(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** How far {@code now} has moved the wanted way from {@code baseline}; negative means backwards. */
        double progressed(double baseline, double now) {
            return this == INCREASE ? now - baseline : baseline - now;
        }
    }

    public static final Codec<TownsteadChangeObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    TownsteadQuery.MAP_CODEC.forGetter(TownsteadChangeObjective::query),
                    Direction.CODEC.fieldOf("direction").forGetter(TownsteadChangeObjective::direction),
                    ExtraCodecs.POSITIVE_INT.xmap(Integer::doubleValue, Double::intValue)
                            .fieldOf("amount").forGetter(TownsteadChangeObjective::amount),
                    StrictCodecs.strictOptional(Codec.DOUBLE, "minimum_final")
                            .forGetter(TownsteadChangeObjective::minimumFinal),
                    StrictCodecs.strictOptional(Codec.DOUBLE, "maximum_final")
                            .forGetter(TownsteadChangeObjective::maximumFinal),
                    StrictCodecs.strictOptional(Codec.BOOL, "baseline_on_accept", true)
                            .forGetter(TownsteadChangeObjective::baselineOnAccept)
            ).apply(instance, TownsteadChangeObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_CHANGE;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return TownsteadObjectives.capabilitiesFor(query);
    }

    @Override
    public int required() {
        return (int) Math.ceil(amount);
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(required(), progress.count());
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= required() && progress.extra().getBoolean(K_FINAL_OK);
    }

    /**
     * Takes the starting reading the moment the quest is accepted, so the player cannot bank progress
     * they made before being asked. With {@code baseline_on_accept} off the first poll takes it
     * instead, which is the right choice for "get them to X from wherever they are now".
     */
    @Override
    public void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                               ServerLevel level) {
        if (baselineOnAccept) {
            captureBaseline(player, active, progress, level);
        }
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        ServerLevel level = (ServerLevel) player.level();
        Entity target = TownsteadObjectives.subjectEntity(query, player, quest, progress, level);
        OptionalDouble reading = read(query, target, level);
        if (reading.isEmpty()) {
            return false; // nothing readable this pass; the baseline and the counter both stand
        }
        if (!TownsteadBaseline.isFrozen(progress)) {
            captureBaseline(player, quest, progress, level);
            return false; // the pass that establishes the start line makes no progress along it
        }
        OptionalDouble baseline = TownsteadBaseline.number(progress);
        if (baseline.isEmpty()
                || !TownsteadBaseline.matches(progress, query.source().id(), pathKey())) {
            return false; // baseline taken from a different question; refuse to compare
        }

        double now = reading.getAsDouble();
        int moved = (int) Math.max(0, Math.floor(direction.progressed(baseline.getAsDouble(), now)));
        boolean finalOk = minimumFinal.map(min -> now >= min).orElse(true)
                && maximumFinal.map(max -> now <= max).orElse(true);

        boolean changed = moved != progress.count()
                || finalOk != progress.extra().getBoolean(K_FINAL_OK);
        progress.setCount(Math.min(required(), moved));
        progress.extra().putBoolean(K_FINAL_OK, finalOk);
        return changed;
    }

    private void captureBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                                 ServerLevel level) {
        Entity target = TownsteadObjectives.subjectEntity(query, player, active, progress, level);
        OptionalDouble reading = read(query, target, level);
        if (reading.isEmpty()) {
            // Refuse to freeze an unreadable start: an "absent" baseline would make every later delta
            // meaningless. The next poll tries again, and the quest simply waits.
            return;
        }
        TownsteadBaseline.freeze(progress, query.source().id(), pathKey(),
                target == null ? null : target.getUUID(), reading.getAsDouble(), level.getGameTime());
        if (target != null) {
            progress.setTargetUuid(target.getUUID()); // bind, so the baseline keeps its villager
        }
    }

    private static OptionalDouble read(TownsteadQuery query, @Nullable Entity target, ServerLevel level) {
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        Object subject = evaluation.subject(query, target);
        if (subject == null) {
            return OptionalDouble.empty();
        }
        Object value = TownsteadPaths.resolve(subject, TownsteadEvaluation.effectivePath(query))
                .orElse(null);
        return value instanceof Number number
                ? OptionalDouble.of(number.doubleValue())
                : OptionalDouble.empty();
    }

    private String pathKey() {
        return String.join(".", query.path());
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        return false; // a delta from a not-yet-taken baseline is zero by definition
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.townstead_change." + direction.id(),
                required(), String.join(".", query.path()));
    }
}
