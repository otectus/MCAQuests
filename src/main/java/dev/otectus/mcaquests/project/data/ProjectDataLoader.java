package dev.otectus.mcaquests.project.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.data.BuiltinPack;
import dev.otectus.mcaquests.data.QuestValidationException;
import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
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
        // enableDefaultQuestPack: an owner who wants only their own content gets only their
        // own content. A datapack that overrides a bundled file keeps its override.
        files = BuiltinPack.filter(files, manager, DIRECTORY);
        if (!McaQuestsConfig.COMMON.enableVillageProjects.get()) {
            ProjectRegistry.replaceAll(Map.of(), List.of());
            UnavailableContent.replace(UnavailableContent.Kind.PROJECT, Map.of());
            return;
        }

        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, ProjectDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> fileOf = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        Map<String, Integer> absentMods = new java.util.TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String[] failure = new String[1];
            java.util.Optional<String> newerFormat = dev.otectus.mcaquests.data.FormatVersion.refusal(entry.getValue());
            newerFormat.ifPresent(message -> failure[0] = message);
            java.util.Optional<ProjectDefinition> parsed = newerFormat.isPresent() ? java.util.Optional.empty()
                    : dev.otectus.mcaquests.data.StrictCodecs.parse(ProjectDefinition.CODEC, JsonOps.INSTANCE,
                            entry.getValue(), message -> failure[0] = message);
            // Content for an optional mod that is not installed is excluded, not malformed (1.7.0).
            if (parsed.isEmpty() && failure[0] != null
                    && !dev.otectus.mcaquests.data.OptionalModNamespaces.excludedForAbsentMod(failure[0], absentMods)) {
                recordError(errors, strict, "Project '" + fileId + "': " + failure[0]);
            }
            parsed
                    .ifPresent(def -> {
                        if (loaded.containsKey(def.id())) {
                            recordError(errors, strict, "Duplicate project id '" + def.id() + "' (from " + fileId + ")");
                            return;
                        }
                        loaded.put(def.id(), def);
                        fileOf.put(def.id(), fileId);
                    });
        }

        // Every phase counts: a Townstead phase two makes the whole project Townstead content, however
        // ordinary its first donation looks, so a base installation never offers phase one of something
        // it can never finish (IntegrationRequirements). Instances already running keep their state and
        // pause; see UnavailableContent.
        UnavailableContent.Collector unavailable = new UnavailableContent.Collector(UnavailableContent.Kind.PROJECT);
        loaded.values().removeIf(def -> IntegrationRequirements.unavailable(def).map(why -> {
            unavailable.add(def.id(), why, UnavailableContent.sourceOf(manager, DIRECTORY, fileOf.get(def.id())),
                    def.displayTitle());
            return true;
        }).orElse(false));

        ProjectValidator.validate(loaded, unavailable.entries().keySet(), errors);
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

        unavailable.publish();
        dev.otectus.mcaquests.data.OptionalModNamespaces.report("project", absentMods);
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
