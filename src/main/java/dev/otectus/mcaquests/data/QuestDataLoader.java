package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
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
 * Datapack reload listener that loads quest JSON from {@code data/<ns>/mcaquests/quests/**.json}
 * (spec section 10). Reloads with {@code /reload}. A malformed quest is logged and skipped; it never
 * crashes the server unless {@code strictJsonValidation} is enabled (spec sections 10, 26).
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/quests";

    public QuestDataLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new QuestDataLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, QuestDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            QuestDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Quest '" + fileId + "': " + message))
                    .ifPresent(def -> {
                        if (loaded.containsKey(def.id())) {
                            recordError(errors, strict, "Duplicate quest id '" + def.id() + "' (from " + fileId + ")");
                            return;
                        }
                        warnOnHeartsRange(def);
                        loaded.put(def.id(), def);
                    });
        }

        QuestChainValidator.validate(loaded, errors);
        TemplateValidator.validate(loaded, errors);
        FailureValidator.validate(loaded, errors);
        if (strict && !errors.isEmpty()) {
            throw new QuestValidationException(errors.get(errors.size() - 1));
        }

        QuestRegistry.replaceAll(loaded, errors);
        McaQuests.LOGGER.info("Loaded {} MCA quest(s) with {} error(s).", loaded.size(), errors.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }

    /** Non-fatal: warn when a hearts reward exceeds the configured clamp (it will be capped at grant). */
    private static void warnOnHeartsRange(QuestDefinition def) {
        int max = McaQuestsConfig.COMMON.maxHeartsReward.get();
        def.rewards().stream()
                .filter(r -> r instanceof HeartsReward)
                .map(r -> ((HeartsReward) r).amount())
                .filter(amount -> amount > max)
                .forEach(amount -> McaQuests.LOGGER.warn(
                        "[MCA: Quests] Quest '{}' hearts reward {} exceeds maxHeartsReward {}; it will be clamped.",
                        def.id(), amount, max));
    }
}
