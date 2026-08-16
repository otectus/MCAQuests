package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.title.TitleDefinition;
import dev.otectus.mcaquests.quest.title.Titles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener for optional title definitions, loaded from
 * {@code data/<ns>/mcaquests/titles/**.json} (spec 0.7.0). Titles function even when undefined; this just
 * supplies display names/scopes. Disabled when {@code enableReputationTiers} is off.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class TitleLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/titles";

    public TitleLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TitleLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        if (!McaQuestsConfig.COMMON.enableReputationTiers.get()) {
            Titles.replaceAll(Map.of());
            return;
        }

        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, TitleDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            TitleDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Title '" + fileId + "': " + message))
                    .ifPresent(def -> {
                        if (TitleValidator.validate(fileId, def, errors)) {
                            loaded.put(fileId, def);
                        } else if (strict) {
                            throw new QuestValidationException("Invalid title: " + fileId);
                        }
                    });
        }

        for (String error : errors) {
            McaQuests.LOGGER.error("[MCA: Quests] {}", error);
        }
        Titles.replaceAll(loaded);
        McaQuests.LOGGER.info("Loaded {} title definition(s) with {} note(s).", loaded.size(), errors.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }
}
