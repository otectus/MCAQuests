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
        // enableDefaultQuestPack: an owner who wants only their own content gets only their
        // own content. A datapack that overrides a bundled file keeps its override.
        files = BuiltinPack.filter(files, manager, DIRECTORY);
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, QuestDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

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

        // Everything above this point reported through recordError, which logs as it goes. The
        // validators below append straight to the list, so their findings were counted in the summary
        // line and never printed — a real error reading as an unexplained "with 1 error(s)".
        int alreadyLogged = errors.size();
        QuestChainValidator.validate(loaded, errors, warnings);
        TemplateValidator.validate(loaded, errors);
        FailureValidator.validate(loaded, errors);
        ObjectiveValidator.validate(loaded, errors, warnings);
        AgeEligibilityValidator.validate(loaded, warnings);
        // New in 1.4.3, and deliberately lenient for one release: it rejects third-party content that has
        // always loaded, so outside strict mode the author gets a loud line rather than a dead server.
        if (strict) {
            TargetGateValidator.validate(loaded, errors, warnings);
        } else {
            TargetGateValidator.validate(loaded, warnings, warnings);
        }
        errors.subList(alreadyLogged, errors.size())
                .forEach(e -> McaQuests.LOGGER.error("[MCA: Quests] {}", e));
        if (strict && !errors.isEmpty()) {
            throw new QuestValidationException(errors.get(errors.size() - 1));
        }

        QuestRegistry.replaceAll(loaded, errors, warnings);
        warnings.forEach(w -> McaQuests.LOGGER.warn("[MCA: Quests] {}", w));
        McaQuests.LOGGER.info("Loaded {} MCA quest(s) with {} error(s), {} warning(s).",
                loaded.size(), errors.size(), warnings.size());
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
