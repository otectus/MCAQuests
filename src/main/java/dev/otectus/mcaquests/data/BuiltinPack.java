package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Honours {@code enableDefaultQuestPack}, the switch that turns off the content this mod ships with.
 *
 * <p>It was declared and documented from the first release — "Load the 165 built-in quests. Set false to
 * ship only your own datapack quests" — and read by absolutely nothing. A server owner who wanted only
 * their own content had no way to get it short of editing the jar.
 *
 * <h2>What "built-in" means</h2>
 *
 * <p>Not "in the {@code mcaquests} namespace": a datapack is allowed to <em>override</em> a bundled file
 * by putting its own version at the same path, and that override is the author's content, not ours. So the
 * test is which pack actually won the merge. Forge names a mod's own resources
 * {@code mod:&lt;modid&gt;}, and a datapack's source id is its pack name, so the winning resource's source
 * tells the two apart exactly.
 *
 * <p>A file whose source cannot be determined is <b>kept</b>. Silently dropping content because a
 * resource-pack implementation named itself unexpectedly would be a far worse failure than loading a
 * built-in the owner asked to be rid of.
 */
public final class BuiltinPack {

    /** How Forge names a mod's own bundled resources. */
    private static final String MOD_SOURCE_PREFIX = "mod:";

    private BuiltinPack() {
    }

    /**
     * Drops this mod's own bundled definitions when {@code enableDefaultQuestPack} is off.
     *
     * <p>Returns {@code files} unchanged when the switch is on, which is the overwhelmingly common case
     * and costs nothing.
     */
    public static <T> Map<ResourceLocation, T> filter(Map<ResourceLocation, T> files,
                                                      ResourceManager manager, String directory) {
        if (McaQuestsConfig.COMMON.enableDefaultQuestPack.get()) {
            return files;
        }
        Map<ResourceLocation, T> kept = new LinkedHashMap<>();
        int dropped = 0;
        for (Map.Entry<ResourceLocation, T> entry : files.entrySet()) {
            if (isBundled(manager, directory, entry.getKey())) {
                dropped++;
            } else {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        if (dropped > 0) {
            McaQuests.LOGGER.info("[MCA: Quests] enableDefaultQuestPack is off: skipped {} built-in {} "
                    + "definition(s).", dropped, directory);
        }
        return kept;
    }

    /**
     * Whether the winning resource behind this id came out of the mod jar.
     *
     * <p>{@code SimpleJsonResourceReloadListener} hands its subclasses a merged map with the file
     * extension already stripped, so the full path has to be rebuilt to ask the manager about it.
     */
    private static boolean isBundled(ResourceManager manager, String directory, ResourceLocation id) {
        ResourceLocation path = new ResourceLocation(id.getNamespace(),
                directory + "/" + id.getPath() + ".json");
        Optional<Resource> resource = manager.getResource(path);
        return resource.map(found -> found.sourcePackId().startsWith(MOD_SOURCE_PREFIX)
                        && found.sourcePackId().contains(McaQuests.MOD_ID))
                .orElse(false);
    }
}
