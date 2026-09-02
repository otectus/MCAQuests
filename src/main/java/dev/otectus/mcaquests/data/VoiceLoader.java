package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.dialogue.VoicePool;
import dev.otectus.mcaquests.quest.dialogue.VoicePools;
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
 * Datapack reload listener for shared villager voice, loaded from
 * {@code data/<ns>/mcaquests/dialogue/**.json}.
 *
 * <p>Follows {@code TitleLoader} exactly, including the {@code enableDefaultQuestPack} filter: an owner
 * who wants only their own content gets only their own content, and a pack that overrides a bundled
 * file keeps its override.
 *
 * <p>A malformed pool is dropped with an error rather than taken as a reason to fail the reload,
 * unless {@code strictJsonValidation} says otherwise — a villager falling back to their old flat line
 * is a far smaller problem than a world that will not load.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class VoiceLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/dialogue";

    public VoiceLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoiceLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        files = BuiltinPack.filter(files, manager, DIRECTORY);

        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, VoicePool> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            VoicePool.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> recordError(errors, strict, "Dialogue pool '" + fileId + "': " + message))
                    .ifPresent(pool -> loaded.put(fileId, pool));
        }

        VoicePools.replaceAll(loaded);
        McaQuests.LOGGER.info("Loaded {} dialogue pool(s) with {} note(s).", loaded.size(), errors.size());
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }
}
