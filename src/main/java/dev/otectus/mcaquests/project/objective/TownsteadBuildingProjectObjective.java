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

import java.util.OptionalInt;

/**
 * A project phase that finishes when the village has the buildings (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_building_project", "building_type": "dock", "minimum_level": 2, "count": 1 }
 * }</pre>
 *
 * <p>Reads the registry, never the world, so a dock-shaped pile of planks does not count and a dock
 * built while nobody was looking does.
 */
public record TownsteadBuildingProjectObjective(String buildingType, int minimumLevel, int count)
        implements PollingProjectObjective {

    public static final Codec<TownsteadBuildingProjectObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("building_type")
                            .forGetter(TownsteadBuildingProjectObjective::buildingType),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_level", 1)
                            .forGetter(TownsteadBuildingProjectObjective::minimumLevel),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1)
                            .forGetter(TownsteadBuildingProjectObjective::count)
            ).apply(instance, TownsteadBuildingProjectObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_BUILDING;
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        if (village.isEmpty() || !TownsteadBridge.Holder.get().has(TownsteadCapability.READ_BUILDING)) {
            return false;
        }
        int have = new TownsteadEvaluation()
                .countBuildings(level, village.getAsInt(), buildingType, minimumLevel);
        if (have == progress.count()) {
            return false;
        }
        progress.setCount(Math.min(count, have));
        return true;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.project.objective.townstead_building",
                count, dev.otectus.mcaquests.quest.TownsteadNames.building(buildingType), minimumLevel);
    }
}
