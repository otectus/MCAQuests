package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener that loads situation JSON from {@code data/<ns>/mcaquests/situations/**.json}
 * (the "Living Village" phase, 0.8.0). Reloads with {@code /reload}. A malformed situation is logged and
 * skipped; it never crashes the server unless {@code strictJsonValidation} is enabled. Mirrors
 * {@link QuestDataLoader}.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class SituationDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/situations";

    public SituationDataLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SituationDataLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, SituationDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            SituationDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Situation '" + fileId + "': " + message))
                    .ifPresent(def -> {
                        if (loaded.containsKey(def.id())) {
                            recordError(errors, strict, "Duplicate situation id '" + def.id() + "' (from " + fileId + ")");
                            return;
                        }
                        loaded.put(def.id(), def);
                    });
        }

        if (strict && !errors.isEmpty()) {
            throw new QuestValidationException(errors.get(errors.size() - 1));
        }

        SituationRegistry.replaceAll(loaded, errors, warnings);
        warnings.forEach(w -> McaQuests.LOGGER.warn("[MCA: Quests] {}", w));
        McaQuests.LOGGER.info("Loaded {} MCA situation(s) with {} error(s), {} warning(s).",
                loaded.size(), errors.size(), warnings.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }
}
