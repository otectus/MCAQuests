package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadBuildings;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.TownsteadNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A project phase that finishes when the village has the buildings (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_building_project", "building_type": "dock", "minimum_level": 2, "count": 1 }
 * }</pre>
 *
 * <p>Reads MCA's building registry, never the world, so a dock-shaped pile of planks does not count and a
 * dock built while nobody was looking does. This is a <b>state</b> objective: a building that already
 * stands when the phase opens counts at once, and one registered later counts on the next sweep
 * ({@code compat.townstead.projectPollIntervalTicks}).
 *
 * <p>Since 1.7.0:
 * <ul>
 *   <li>Only buildings MCA considers <b>complete</b> count — the same buildings Townstead's village
 *       spirit counts, so the two objectives of a phase can no longer disagree about one inn. A
 *       registered building missing a required block is reported as such, with MCA's requirements.</li>
 *   <li>A village that cannot be read is unknown, not empty: progress is left as it is rather than
 *       dropped to zero by a failed lookup.</li>
 *   <li>The clamped count is compared before anything is reported, so a village with more buildings
 *       than asked for no longer re-syncs every player on every sweep.</li>
 * </ul>
 */
public record TownsteadBuildingProjectObjective(String buildingType, int minimumLevel, int count)
        implements TownsteadProjectObjective {

    /** How the registry is read; replaced only by tests, which have no level to read from. */
    @FunctionalInterface
    interface CensusReader {
        TownsteadEvaluation.BuildingCensus read(ServerLevel level, int villageId, String type, int minimumLevel);
    }

    private static final CensusReader LIVE_CENSUS = (level, villageId, type, minimumLevel) ->
            new TownsteadEvaluation().buildingCensus(level, villageId, type, minimumLevel);
    static volatile CensusReader census = LIVE_CENSUS;

    static void setCensusForTest(@javax.annotation.Nullable CensusReader replacement) {
        census = replacement == null ? LIVE_CENSUS : replacement;
    }

    static final String K_READABLE = "building_readable";
    static final String K_COMPLETE = "building_complete";
    static final String K_INCOMPLETE = "building_incomplete";

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
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_BUILDING);
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
        TownsteadEvaluation.BuildingCensus seen = census.read(level, village.getAsInt(), buildingType, minimumLevel);
        CompoundTag extra = progress.extra();
        extra.putBoolean(K_READABLE, seen.readable());
        if (!seen.readable()) {
            return false; // unknown is not zero: keep what was last seen
        }
        extra.putInt(K_COMPLETE, seen.complete());
        extra.putInt(K_INCOMPLETE, seen.incomplete());
        int next = Math.min(count, seen.complete());
        if (next == progress.count()) {
            return false;
        }
        // Live state, deliberately not a high-water mark: a demolished dock is no longer a dock.
        progress.setCount(next);
        return true;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.project.objective.townstead_building",
                count, TownsteadNames.building(buildingType), minimumLevel);
    }

    @Override
    public ProjectObjectiveStatus status(ProjectObjectiveContext context) {
        ProjectObjectiveStatus base = TownsteadProjectObjective.super.status(context);
        if (base == ProjectObjectiveStatus.IN_PROGRESS && context.progress().extra().contains(K_READABLE)
                && !context.progress().extra().getBoolean(K_READABLE)) {
            return ProjectObjectiveStatus.UNOBSERVED;
        }
        return base;
    }

    @Override
    public List<Component> explain(ProjectObjectiveContext context) {
        List<Component> lines = new ArrayList<>();
        Component name = TownsteadNames.building(buildingType);
        lines.add(Component.translatable("mcaquests.project.help.building.rule", count, name));
        CompoundTag extra = context.progress().extra();
        if (extra.contains(K_READABLE) && !extra.getBoolean(K_READABLE)) {
            lines.add(Component.translatable("mcaquests.project.help.building.unreadable"));
        } else if (extra.contains(K_COMPLETE)) {
            lines.add(Component.translatable("mcaquests.project.help.building.seen",
                    extra.getInt(K_COMPLETE), name, extra.getInt(K_INCOMPLETE)));
            if (extra.getInt(K_INCOMPLETE) > 0) {
                lines.add(Component.translatable("mcaquests.project.help.building.incomplete", name));
            }
        }
        Map<ResourceLocation, Integer> requirements = McaCompat.buildingRequirements(
                TownsteadBuildings.normalise(buildingType));
        if (!requirements.isEmpty()) {
            lines.add(Component.translatable("mcaquests.project.help.building.requires", name,
                    requirementList(requirements)));
        }
        lines.add(Component.translatable("mcaquests.project.help.building.register"));
        return lines;
    }

    private static Component requirementList(Map<ResourceLocation, Integer> requirements) {
        MutableComponent out = Component.empty();
        int i = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : requirements.entrySet()) {
            if (i++ > 0) {
                out.append(Component.literal(", "));
            }
            ResourceLocation id = entry.getKey();
            // MCA keys a requirement by block or block-tag id; a block name reads best, a tag's path next.
            Component label = BuiltInRegistries.BLOCK.containsKey(id)
                    ? BuiltInRegistries.BLOCK.get(id).getName()
                    : DisplayNames.tagName(id);
            out.append(Component.translatable("mcaquests.project.help.building.requirement", entry.getValue(), label));
        }
        return out;
    }
}
