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
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * A project phase that finishes when enough of the village can do the job (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_workforce_project",
 *   "professions": ["minecraft:farmer", "minecraft:fisherman"], "minimum_tier": 2, "count": 3 }
 * }</pre>
 *
 * <p>Only loaded residents can be read, so this counts what can actually be seen. That makes it a
 * "come and look at your village" objective rather than a bookkeeping one, which is the honest
 * behaviour given the same limitation applies to every other resident-wide check.
 */
public record TownsteadWorkforceProjectObjective(List<String> professions, int minimumTier, int count)
        implements PollingProjectObjective {

    public static final Codec<TownsteadWorkforceProjectObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.listOf().fieldOf("professions")
                            .forGetter(TownsteadWorkforceProjectObjective::professions),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "minimum_tier", 1)
                            .forGetter(TownsteadWorkforceProjectObjective::minimumTier),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1)
                            .forGetter(TownsteadWorkforceProjectObjective::count)
            ).apply(instance, TownsteadWorkforceProjectObjective::new));

    public TownsteadWorkforceProjectObjective {
        professions = List.copyOf(professions);
    }

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_WORKFORCE;
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (village.isEmpty() || !bridge.has(TownsteadCapability.READ_PROFESSION)) {
            return false;
        }
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        int qualified = 0;
        for (Entity resident : McaCompat.loadedVillageResidents(level, village.getAsInt())) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view != null && matches(view) && view.professionLevel() >= minimumTier) {
                qualified++;
            }
        }
        if (qualified <= progress.count()) {
            return false; // a villager who wandered out of range has not stopped being a farmer
        }
        progress.setCount(Math.min(count, qualified));
        return true;
    }

    private boolean matches(TownsteadVillagerView view) {
        String id = view.professionId().toLowerCase(Locale.ROOT);
        for (String wanted : professions) {
            if (id.equals(wanted.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.project.objective.townstead_workforce",
                count, minimumTier);
    }
}
