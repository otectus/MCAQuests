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
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
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
        // enableDefaultQuestPack: an owner who wants only their own content gets only their
        // own content. A datapack that overrides a bundled file keeps its override.
        files = BuiltinPack.filter(files, manager, DIRECTORY);
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, SituationDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> fileOf = new LinkedHashMap<>();

        Map<String, Integer> absentMods = new java.util.TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String[] failure = new String[1];
            java.util.Optional<SituationDefinition> parsed = dev.otectus.mcaquests.data.StrictCodecs.parse(SituationDefinition.CODEC,
                    JsonOps.INSTANCE, entry.getValue(), message -> failure[0] = message);
            // Content for an optional mod that is not installed is excluded, not malformed (1.7.0).
            if (parsed.isEmpty() && failure[0] != null
                    && !dev.otectus.mcaquests.data.OptionalModNamespaces.excludedForAbsentMod(failure[0], absentMods)) {
                recordError(errors, strict, "Situation '" + fileId + "': " + failure[0]);
            }
            parsed
                    .ifPresent(def -> {
                        if (loaded.containsKey(def.id())) {
                            recordError(errors, strict, "Duplicate situation id '" + def.id() + "' (from " + fileId + ")");
                            return;
                        }
                        loaded.put(def.id(), def);
                        fileOf.put(def.id(), fileId);
                    });
        }

        // A situation whose trigger or offer needs an optional mod this installation lacks never opens,
        // so it is not loaded at all (IntegrationRequirements); an open one keeps its descriptor.
        UnavailableContent.Collector unavailable = new UnavailableContent.Collector(UnavailableContent.Kind.SITUATION);
        loaded.values().removeIf(def -> dev.otectus.mcaquests.quest.IntegrationRequirements.unavailable(def)
                .map(why -> {
                    unavailable.add(def.id(), why, UnavailableContent.sourceOf(manager, DIRECTORY, fileOf.get(def.id())),
                    def.offer().title().map(text -> text.resolve()).orElseGet(() -> net.minecraft.network.chat.Component.literal(def.id().toString())));
                    return true;
                }).orElse(false));

        // The first cross-reference validation situations have ever had. Until 1.4.3 a situation offer
        // was parsed and nothing else, which is how two of the shipped ones carried a family target with
        // no gate at all for several releases. An error since 1.7.0: outside strict mode the situation is
        // skipped at load.
        int alreadyLogged = errors.size();
        TargetGateValidator.enforceSituations(loaded, errors, warnings, strict);
        errors.subList(alreadyLogged, errors.size())
                .forEach(e -> McaQuests.LOGGER.error("[MCA: Quests] {}", e));

        if (strict && !errors.isEmpty()) {
            throw new QuestValidationException(errors.get(errors.size() - 1));
        }

        unavailable.publish();
        dev.otectus.mcaquests.data.OptionalModNamespaces.report("situation", absentMods);
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
