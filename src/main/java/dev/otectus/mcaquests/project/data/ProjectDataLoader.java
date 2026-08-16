package dev.otectus.mcaquests.project.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.data.QuestValidationException;
import dev.otectus.mcaquests.project.ProjectDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener for community projects, loaded from
 * {@code data/<ns>/mcaquests/projects/**.json} (spec 0.4.0). Mirrors {@code QuestDataLoader}: a
 * malformed project is logged and skipped, never crashing the server unless {@code strictJsonValidation}
 * is enabled. Disabled entirely when {@code enableVillageProjects} is off.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ProjectDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/projects";

    public ProjectDataLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ProjectDataLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        if (!McaQuestsConfig.COMMON.enableVillageProjects.get()) {
            ProjectRegistry.replaceAll(Map.of(), List.of());
            return;
        }

        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, ProjectDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ProjectDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Project '" + fileId + "': " + message))
                    .ifPresent(def -> {
                        if (loaded.containsKey(def.id())) {
                            recordError(errors, strict, "Duplicate project id '" + def.id() + "' (from " + fileId + ")");
                            return;
                        }
                        loaded.put(def.id(), def);
                    });
        }

        ProjectValidator.validate(loaded, errors);
        if (strict) {
            errors.stream()
                    .filter(e -> !ProjectValidator.isWarning(e))
                    .reduce((first, second) -> second)
                    .ifPresent(last -> {
                        throw new QuestValidationException(last);
                    });
        }

        for (String error : errors) {
            if (ProjectValidator.isWarning(error)) {
                McaQuests.LOGGER.warn("[MCA: Quests] {}", error);
            } else {
                McaQuests.LOGGER.error("[MCA: Quests] {}", error);
            }
        }

        ProjectRegistry.replaceAll(loaded, errors);
        McaQuests.LOGGER.info("Loaded {} MCA project(s) with {} validation note(s).", loaded.size(), errors.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }
}
