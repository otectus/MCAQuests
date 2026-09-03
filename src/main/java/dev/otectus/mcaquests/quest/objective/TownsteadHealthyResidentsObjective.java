package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Complete when enough of a village's residents are in good shape, and stay that way
 * (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_healthy_residents",
 *   "minimum_observed": 4,
 *   "minimum_fraction": 0.75,
 *   "hunger_min": 60,
 *   "energy_min": 8,
 *   "hold_ticks": 1200
 * }
 * }</pre>
 *
 * <p>Two safeguards make this honest rather than exploitable. Only <b>loaded</b> villagers can be read,
 * so {@code minimum_observed} sets a floor on how many the check must actually see — otherwise a
 * player could stand somewhere with one contented villager in range and satisfy "the village is well
 * fed". And residents are visited in a capped, rotating window, so a large village costs a bounded
 * amount of work per second and still gets seen in full over a few passes.
 */
public record TownsteadHealthyResidentsObjective(int minimumObserved, double minimumFraction,
                                                 OptionalInt hungerMin, OptionalInt thirstMin,
                                                 OptionalInt energyMin,
                                                 boolean requireNotCollapsed,
                                                 double minimumLoadedFraction, int holdTicks)
        implements PollingObjective, TownsteadObjective {

    private static final int TICKS_PER_SECOND = 20;

    /** The share of the village that must be observable before the hold timer may run (spec 5.7). */
    private static final double DEFAULT_LOADED_FRACTION = 0.50D;

    public static final MapCodec<TownsteadHealthyResidentsObjective> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_observed", 3)
                            .forGetter(TownsteadHealthyResidentsObjective::minimumObserved),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.0D, 1.0D), "minimum_fraction", 0.75D)
                            .forGetter(TownsteadHealthyResidentsObjective::minimumFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "hunger_min")
                            .forGetter((TownsteadHealthyResidentsObjective o) -> box(o.hungerMin())),
                    StrictCodecs.strictOptional(Codec.intRange(0, 20), "thirst_min")
                            .forGetter((TownsteadHealthyResidentsObjective o) -> box(o.thirstMin())),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "energy_min")
                            .forGetter((TownsteadHealthyResidentsObjective o) -> box(o.energyMin())),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_not_collapsed", true)
                            .forGetter(TownsteadHealthyResidentsObjective::requireNotCollapsed),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.0D, 1.0D), "minimum_loaded_fraction",
                                    DEFAULT_LOADED_FRACTION)
                            .forGetter(TownsteadHealthyResidentsObjective::minimumLoadedFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", TICKS_PER_SECOND)
                            .forGetter(TownsteadHealthyResidentsObjective::holdTicks)
            ).apply(instance, (observed, fraction, hunger, thirst, energy, collapsed, loaded, hold) ->
                    new TownsteadHealthyResidentsObjective(observed, fraction, unbox(hunger), unbox(thirst),
                            unbox(energy), collapsed, loaded, hold)));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_HEALTHY_RESIDENTS;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_VILLAGER, TownsteadCapability.READ_NEEDS);
    }

    @Override
    public int required() {
        return Math.max(1, holdTicks / TICKS_PER_SECOND);
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(required(), (int) (progress.elapsedTicks() / TICKS_PER_SECOND));
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.elapsedTicks() >= holdTicks;
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        if (isSatisfied(player, progress)) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        // Real time since the last poll rather than one flat second per poll -- see
        // TownsteadObjectives.elapsedSincePoll; polls run every townsteadPollIntervalTicks.
        long elapsed = TownsteadObjectives.elapsedSincePoll(progress, level.getGameTime(),
                McaQuestsConfig.COMMON.townsteadPollIntervalTicks.get());
        Entity giver = level.getEntity(quest.villagerUuid());
        List<Entity> residents = TownsteadTargetResolver.residents(level, giver, level.getGameTime());
        if (residents.size() < minimumObserved || !enoughOfTheVillageIsLoaded(level, giver, residents)) {
            // Too few visible to make a claim about the village. Not a failure -- come back with more
            // of the village loaded -- but not evidence of health either, so the timer does not run.
            return resetIfRunning(progress);
        }

        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        int observed = 0;
        int healthy = 0;
        for (Entity resident : residents) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view == null) {
                continue;
            }
            observed++;
            if (isHealthy(view.needs())) {
                healthy++;
            }
        }
        if (observed < minimumObserved) {
            return resetIfRunning(progress);
        }
        if ((double) healthy / observed < minimumFraction) {
            return resetIfRunning(progress);
        }

        progress.addElapsed(elapsed);
        return true;
    }

    /**
     * True when enough of the village's <em>roll</em> is observable to make a claim about it (spec 5.7).
     *
     * <p>{@code minimum_observed} alone is not enough. In a village of forty, seeing three contented
     * residents is not evidence that the village is fed -- it is evidence about three people. This
     * measures the loaded residents against MCA's full resident roll, so an unloaded population is
     * never counted as healthy; the hold simply waits.
     *
     * <p>An unreadable roll is treated as satisfied rather than as a permanent block: without MCA's
     * resident list there is no denominator, and refusing to ever run would strand the quest.
     */
    private boolean enoughOfTheVillageIsLoaded(ServerLevel level, @Nullable Entity giver,
                                               List<Entity> observed) {
        if (minimumLoadedFraction <= 0.0D || giver == null) {
            return true;
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        if (villageId.isEmpty()) {
            return true;
        }
        int roll = McaCompat.villageResidentUuids(level, villageId.getAsInt()).size();
        if (roll <= 0) {
            return true;
        }
        // The resident window is capped per pass, so compare against the true loaded count rather than
        // against the sample this pass happened to draw.
        int loaded = Math.max(observed.size(),
                McaCompat.loadedVillageResidents(level, villageId.getAsInt()).size());
        return (double) loaded / roll >= minimumLoadedFraction;
    }

    private boolean isHealthy(TownsteadNeedsView needs) {
        if (requireNotCollapsed && needs.collapsed()) {
            return false;
        }
        if (hungerMin.isPresent() && needs.hunger() < hungerMin.getAsInt()) {
            return false;
        }
        if (thirstMin.isPresent() && needs.thirst() < thirstMin.getAsInt()) {
            return false;
        }
        return energyMin.isEmpty() || needs.energy() >= energyMin.getAsInt();
    }

    private static boolean resetIfRunning(ObjectiveProgress progress) {
        if (progress.elapsedTicks() > 0L) {
            progress.resetElapsed();
            return true;
        }
        return false;
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        return false; // a hold always takes real time
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.townstead_healthy_residents",
                (int) Math.round(minimumFraction * 100), required());
    }
}
