package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.api.event.ReputationTierReachedEvent;
import dev.otectus.mcaquests.api.event.TitleGrantedEvent;
import dev.otectus.mcaquests.quest.title.TitleScope;
import dev.otectus.mcareputation.api.McaReputationApi;
import dev.otectus.mcareputation.api.event.ReputationTierChangedEvent;
import dev.otectus.mcareputation.api.event.ReputationTitleGrantedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registers Quests' side of the MCA: Reputation integration and translates Reputation's events back
 * into the ones Quests has always published (spec §29.8).
 *
 * <p><b>Only ever loaded when Reputation is present</b> — {@link dev.otectus.mcaquests.compat.ReputationBridge}
 * registers this class on the event bus after the mod-present check, never at class-init time. That is
 * why it can name {@code mcareputation} types freely.
 *
 * <h2>Event translation, exactly once</h2>
 *
 * <p>Other mods and title-chain quests already listen for {@link ReputationTierReachedEvent} and
 * {@link TitleGrantedEvent}. Those consumers must keep working when standing moves to Reputation, so
 * each canonical event is re-posted in Quests' vocabulary.
 *
 * <p>There is no double-post risk, and the reason is structural rather than a guard: with Reputation
 * installed, Quests' own {@code LegacyReputationBackend} — the only thing that posts those events
 * natively — is not in use at all. Exactly one of the two paths is ever live.
 *
 * <p>Only the <em>first time</em> a tier is reached is translated. Quests'
 * {@code ReputationTierReachedEvent} has always meant "a new best with this village", and firing it on
 * every oscillation across a threshold would change what existing listeners believe it means.
 */
public final class QuestsReputationEvents {

    private static QuestsReputationMirror mirror;
    private static QuestsLegacyImportProvider importProvider;

    private QuestsReputationEvents() {
    }

    /** Called by the bridge once the backend has been chosen. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(QuestsReputationEvents.class);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            mirror = new QuestsReputationMirror(server);
            McaReputationApi.registerMirror(mirror);

            importProvider = new QuestsLegacyImportProvider();
            McaReputationApi.registerImportProvider(importProvider);

            McaQuests.LOGGER.info("[MCA: Quests] registered the fallback mirror and the legacy "
                    + "reputation import provider with MCA: Reputation.");
        } catch (Throwable t) {
            McaQuests.LOGGER.error("[MCA: Quests] could not register with MCA: Reputation; standing will "
                    + "still work, but Quests' fallback copy will not be kept up to date.", t);
        }
    }

    /** Unregisters on shutdown so a second world in the same JVM does not mirror into the first. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            if (mirror != null) {
                McaReputationApi.unregisterMirror(mirror);
                mirror = null;
            }
            if (importProvider != null) {
                McaReputationApi.unregisterImportProvider(importProvider);
                importProvider = null;
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] failed to unregister from MCA: Reputation", t);
        }
    }

    @SubscribeEvent
    public static void onTierChanged(ReputationTierChangedEvent event) {
        if (!event.upward() || !event.firstTime()) {
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.post(new ReputationTierReachedEvent(
                    event.player().orElse(null),
                    event.community().villageId(),
                    // Quests' event carries its own ladder id; the canonical ladder is the same
                    // ladder under a different namespace (see ReputationTiers' alias), so consumers
                    // that compare against mcaquests:default keep matching.
                    dev.otectus.mcaquests.quest.reputation.ReputationTiers.DEFAULT_ID,
                    event.newTierId(),
                    event.newIndex()));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] could not translate a tier change into the legacy "
                    + "Quests event", t);
        }
    }

    @SubscribeEvent
    public static void onTitleGranted(ReputationTitleGrantedEvent event) {
        try {
            event.player().ifPresent(player -> MinecraftForge.EVENT_BUS.post(new TitleGrantedEvent(
                    player,
                    event.title(),
                    event.scope() == dev.otectus.mcareputation.reputation.TitleScope.GLOBAL
                            ? TitleScope.GLOBAL
                            : TitleScope.VILLAGE,
                    event.communityOrEmpty()
                            .map(community -> java.util.OptionalInt.of(community.villageId()))
                            .orElseGet(java.util.OptionalInt::empty))));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] could not translate a title grant into the legacy "
                    + "Quests event", t);
        }
    }
}
