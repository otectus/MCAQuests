package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of project objective types and the dispatch {@link Codec} that parses a JSON objective by
 * its {@code "type"} field (spec 0.4.0). Mirrors {@code ObjectiveTypes}; kept separate because shared
 * project progress and per-player quest progress are different models.
 */
public final class ProjectObjectiveTypes {

    private static final Map<ResourceLocation, ProjectObjectiveType<?>> BY_ID = new LinkedHashMap<>();

    public static final ProjectObjectiveType<DonateItemObjective> DONATE_ITEM =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "donate_item"), DonateItemObjective.CODEC);
    public static final ProjectObjectiveType<ProjectKillObjective> PROJECT_KILL_ENTITY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_kill_entity"), ProjectKillObjective.CODEC);
    public static final ProjectObjectiveType<ProjectPlaceBlockObjective> PROJECT_PLACE_BLOCK =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_place_block"), ProjectPlaceBlockObjective.CODEC);
    public static final ProjectObjectiveType<ProjectTalkObjective> PROJECT_TALK_TO_PROFESSION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "project_talk_to_profession"), ProjectTalkObjective.CODEC);

    public static final Codec<ProjectObjectiveType<?>> TYPE_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                ProjectObjectiveType<?> type = BY_ID.get(id);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown project objective type: " + id);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<ProjectObjective> CODEC =
            TYPE_CODEC.dispatch("type", ProjectObjective::type, type -> dev.otectus.mcaquests.data.StrictCodecs.dispatchMap(type.codec()));

    private ProjectObjectiveTypes() {
    }

    public static <T extends ProjectObjective> ProjectObjectiveType<T> register(ResourceLocation id, Codec<T> codec) {
        ProjectObjectiveType<T> type = new ProjectObjectiveType<>(id, codec);
        if (BY_ID.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate project objective type id: " + id);
        }
        return type;
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    /** Forces class-load so the built-in types register before first use. */
    public static void bootstrap() {
    }
}
