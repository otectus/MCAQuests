package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
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
                                                         OptionalInt hungerMin, OptionalInt energyMin,
                                                         int holdTicks)
        implements PollingProjectObjective {

    private static final String K_HELD = "townstead_held_ticks";

    public static final Codec<TownsteadResidentWellbeingProjectObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_observed", 3)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::minimumObserved),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.0D, 1.0D), "minimum_fraction", 0.75D)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::minimumFraction),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "hunger_min")
                            .forGetter((TownsteadResidentWellbeingProjectObjective o) -> box(o.hungerMin())),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "energy_min")
                            .forGetter((TownsteadResidentWellbeingProjectObjective o) -> box(o.energyMin())),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", 1200)
                            .forGetter(TownsteadResidentWellbeingProjectObjective::holdTicks)
            ).apply(instance, (observed, fraction, hunger, energy, hold) ->
                    new TownsteadResidentWellbeingProjectObjective(observed, fraction, unbox(hunger),
                            unbox(energy), hold)));

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
        for (Entity resident : McaCompat.loadedVillageResidents(level, village.getAsInt())) {
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

    private boolean isWell(TownsteadNeedsView needs) {
        if (needs.collapsed()) {
            return false;
        }
        if (hungerMin.isPresent() && needs.hunger() < hungerMin.getAsInt()) {
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
