package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
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
 * Datapack reload listener for reputation tier ladders, loaded from
 * {@code data/<ns>/mcaquests/reputation_tiers/**.json} (spec 0.7.0). Mirrors {@code ProjectDataLoader}:
 * a malformed or structurally-invalid ladder is logged and skipped, never crashing the server unless
 * {@code strictJsonValidation} is enabled. Disabled entirely when {@code enableReputationTiers} is off,
 * in which case {@link ReputationTiers#getDefault()} falls back to the built-in ladder.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ReputationTierLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/reputation_tiers";

    public ReputationTierLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ReputationTierLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        if (!McaQuestsConfig.COMMON.enableReputationTiers.get()) {
            ReputationTiers.replaceAll(Map.of());
            return;
        }

        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, ReputationTierSet> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ReputationTierSet.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Reputation tiers '" + fileId + "': " + message))
                    .ifPresent(set -> {
                        if (ReputationTierValidator.validate(fileId, set, errors)) {
                            loaded.put(fileId, set);
                        } else if (strict) {
                            throw new QuestValidationException("Invalid reputation tier set: " + fileId);
                        }
                    });
        }

        for (String error : errors) {
            McaQuests.LOGGER.error("[MCA: Quests] {}", error);
        }
        ReputationTiers.replaceAll(loaded);
        McaQuests.LOGGER.info("Loaded {} reputation tier ladder(s) with {} note(s).", loaded.size(), errors.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }
}
