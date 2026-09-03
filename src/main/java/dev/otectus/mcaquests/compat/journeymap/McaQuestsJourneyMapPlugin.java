package dev.otectus.mcaquests.compat.journeymap;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.map.ClientMapWaypointRegistry;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * The one class JourneyMap calls, and the only door into this package.
 *
 * <h2>Why this is not reflective, when everything else here is</h2>
 *
 * <p>JourneyMap's documented entry point is a class that <em>carries</em> an annotation and
 * <em>implements</em> an interface. Neither can be produced reflectively: an annotation has to be in
 * the class file and an interface has to be in the {@code implements} clause. The old integration got
 * around that by reflecting the internal {@code journeymap.api.client.impl.ClientAPI.INSTANCE} enum
 * and calling it with an unregistered mod id — real behaviour of a real build, promised by nobody, and
 * removable in a patch release that the declared dependency range would happily accept.
 *
 * <p>So this package is compiled against {@code journeymap-api-forge}, and everything that follows
 * from that is arranged around one rule: <b>nothing outside this package may name anything in it.</b>
 * JourneyMap's own annotation scan is the only thing that ever loads it, and JourneyMap only scans
 * when JourneyMap is installed. A dedicated server, or a client without the mod, never touches these
 * classes at all — {@code NoMinimapStaticLinkTest} fails the build if that stops being true.
 *
 * <h2>The callback is not on the client thread</h2>
 *
 * <p>{@link #initialize} runs when JourneyMap decides, on a parallel-dispatch worker, before or after
 * this mod's own client setup. So it touches exactly two first-party classes, both deliberately built
 * for that: a concurrent registry, and an atomic flag. Nothing here reads {@code Minecraft}.
 */
@JourneyMapPlugin(apiVersion = McaQuestsJourneyMapPlugin.API_VERSION)
public final class McaQuestsJourneyMapPlugin implements IClientPlugin {

    /**
     * The JourneyMap client API generation this package is written against, not a mod version.
     *
     * <p>It is JourneyMap's number rather than ours, so it stays here rather than in
     * {@code gradle.properties}: it changes when JourneyMap changes its API, at which point this code
     * changes with it.
     */
    static final String API_VERSION = "2.0.0";

    /** The registry key, the config key, and the id diagnostics print. */
    static final String BACKEND_ID = "journeymap";

    /** JourneyMap instantiates this itself, so the no-argument constructor is the contract. */
    public McaQuestsJourneyMapPlugin() {
    }

    @Override
    public String getModId() {
        return McaQuests.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        ClientMapWaypointRegistry.register(BACKEND_ID, new JourneyMapWaypointBackend(api));
        McaQuests.LOGGER.info("[MCA: Quests] Minimap — JourneyMap client API {} bound.", API_VERSION);
    }
}
