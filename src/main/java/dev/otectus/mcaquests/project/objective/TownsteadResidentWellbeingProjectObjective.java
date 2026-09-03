package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A project phase that finishes when the village has been well for long enough (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_resident_wellbeing_project",
 *   "minimum_observed": 5, "minimum_fraction": 0.8, "hunger_min": 60, "hold_ticks": 6000 }
 * }</pre>
 *
 * <p>The hold runs in project time, not player time: it accrues once per sweep however many players
 * are online, so a busy server does not finish this five times faster than a quiet one.
 */
public record TownsteadResidentWellbeingProjectObjective(int minimumObserved, double minimumFraction,
                                                         OptionalInt hungerMin, OptionalInt thirstMin,
                                                         OptionalInt energyMin,
                                                         boolean requireNotCollapsed,
                                                         double minimumLoadedFraction,
                                                         int holdTicks)
        implements PollingProjectObjective {

    private static final String K_HELD = "townstead_held_ticks";

    /** Spec 5.7. Matches the personal objective, so the two cannot disagree about the same village. */
    private static final double DEFAULT_LOADED_FRACTION = 0.50D;

    public static final MapCodec<TownsteadResidentWellbeingProjectObjective> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_observed", 3)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::minimumObserved),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.0D, 1.0D), "minimum_fraction", 0.75D)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::minimumFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "hunger_min")
                            .forGetter((TownsteadResidentWellbeingProjectObjective o) -> box(o.hungerMin())),
                    StrictCodecs.strictOptional(Codec.intRange(0, 20), "thirst_min")
                            .forGetter((TownsteadResidentWellbeingProjectObjective o) -> box(o.thirstMin())),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "energy_min")
                            .forGetter((TownsteadResidentWellbeingProjectObjective o) -> box(o.energyMin())),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_not_collapsed", true)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::requireNotCollapsed),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.0D, 1.0D), "minimum_loaded_fraction",
                                    DEFAULT_LOADED_FRACTION)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::minimumLoadedFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", 1200)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::holdTicks)
            ).apply(instance, (observed, fraction, hunger, thirst, energy, collapsed, loaded, hold) ->
                    new TownsteadResidentWellbeingProjectObjective(observed, fraction, unbox(hunger),
                            unbox(thirst), unbox(energy), collapsed, loaded, hold)));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_RESIDENT_WELLBEING;
    }

    @Override
    public int required() {
        return Math.max(1, holdTicks / 20);
    }

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (village.isEmpty() || !bridge.has(TownsteadCapability.READ_NEEDS)) {
            return false;
        }
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        int observed = 0;
        int well = 0;
        List<Entity> residents = McaCompat.loadedVillageResidents(level, village.getAsInt());
        if (!enoughOfTheVillageIsLoaded(level, village.getAsInt(), residents.size())) {
            // An unloaded population is not a well one. The hold waits rather than banking a phase on
            // the handful of residents who happened to be in render distance.
            return reset(progress);
        }
        for (Entity resident : residents) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view == null) {
                continue;
            }
            observed++;
            if (isWell(view.needs())) {
                well++;
            }
        }
        if (observed < minimumObserved || (double) well / observed < minimumFraction) {
            return reset(progress);
        }

        int interval = dev.otectus.mcaquests.McaQuestsConfig.COMMON.townsteadProjectPollIntervalTicks.get();
        long held = progress.extra().getLong(K_HELD) + interval;
        progress.extra().putLong(K_HELD, held);
        int seconds = Math.min(required(), (int) (held / 20));
        if (seconds == progress.count()) {
            return false;
        }
        progress.setCount(seconds);
        return true;
    }

    /**
     * True when enough of the village's roll is observable to make a claim about it (spec 5.7).
     * An unreadable roll counts as satisfied: with no denominator there is nothing to compare, and
     * refusing to ever run would strand the phase.
     */
    private boolean enoughOfTheVillageIsLoaded(ServerLevel level, int villageId, int loaded) {
        if (minimumLoadedFraction <= 0.0D) {
            return true;
        }
        int roll = McaCompat.villageResidentUuids(level, villageId).size();
        return roll <= 0 || (double) loaded / roll >= minimumLoadedFraction;
    }

    private boolean isWell(TownsteadNeedsView needs) {
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

    private static boolean reset(SharedObjectiveProgress progress) {
        if (progress.extra().getLong(K_HELD) == 0L && progress.count() == 0) {
            return false;
        }
        progress.extra().putLong(K_HELD, 0L);
        progress.setCount(0);
        return true;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.project.objective.townstead_wellbeing",
                (int) Math.round(minimumFraction * 100), required());
    }
}
