package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.CompatRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Path;

/**
 * Mounts the {@link CompatPacks} whose requirements hold, as built-in server datapacks.
 *
 * <p>The repository source runs every time the server data repository is built — at world load and on
 * every {@code /reload} — so it re-probes first and decides again. That is what makes the answer
 * follow the installation rather than the moment the game started: a {@code /reload} after a config
 * change, or a world opened with a different mod set, mounts exactly what is usable now, and a pack
 * that stops being usable simply is not offered on the next build.
 *
 * <p>Mounted at {@link Pack.Position#TOP} and marked built-in, so a datapack an owner installs still
 * wins: our content is a default to be overridden, not a claim on the path.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatPackFinder {

    /** Where the folders live inside the jar. */
    private static final String ROOT = "compatpacks";

    private CompatPackFinder() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> {
            // Registry contents have frozen by the time a data repository is built, so this is the
            // first point at which "is that dragon actually registered?" has a true answer.
            CompatRegistry registry = CompatRegistry.get();
            registry.reprobeAll("pack_finder", null);

            IModFile modFile = ownModFile();
            if (modFile == null) {
                McaQuests.LOGGER.warn("[MCA: Quests] Could not find this mod's own file; no compat "
                        + "datapack can be mounted.");
                return;
            }
            for (ConditionalCompatPack pack : CompatPacks.all()) {
                if (!pack.isEnabled(registry)) {
                    McaQuests.LOGGER.debug("[MCA: Quests] Compat datapack '{}' is not needed on this "
                            + "installation; not mounting it.", pack.id());
                    continue;
                }
                mount(consumer, modFile, pack);
            }
        });
    }

    private static void mount(java.util.function.Consumer<Pack> consumer, IModFile modFile,
                              ConditionalCompatPack pack) {
        String packId = McaQuests.MOD_ID + "/" + pack.id();
        Path root = modFile.findResource(ROOT, pack.folder());
        Pack built = Pack.readMetaAndCreate(packId,
                Component.translatable("mcaquests.compatpack." + pack.id()),
                true,
                id -> new PathPackResources(id, true, root),
                PackType.SERVER_DATA,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (built == null) {
            // readMetaAndCreate answers null for a missing or unreadable pack.mcmeta. That is a build
            // problem, not a player one, so it is reported and skipped rather than thrown.
            McaQuests.LOGGER.info("[MCA: Quests] Compat datapack '{}' has no readable pack.mcmeta at {}; "
                    + "skipping it.", packId, root);
            return;
        }
        consumer.accept(built);
        McaQuests.LOGGER.info("[MCA: Quests] Mounted compat datapack '{}'.", packId);
    }

    /** This mod's own jar (or classes directory in dev), or {@code null} if Forge cannot name it. */
    private static IModFile ownModFile() {
        IModFileInfo info = ModList.get().getModFileById(McaQuests.MOD_ID);
        return info == null ? null : info.getFile();
    }
}
