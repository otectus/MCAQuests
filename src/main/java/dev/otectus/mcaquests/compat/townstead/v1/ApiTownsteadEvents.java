package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.EventsApi;
import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent;
import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerCollapsedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerDiedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerLifeStageChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerRecoveredEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerTierChangedEvent;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadLifeStageView;
import dev.otectus.mcaquests.compat.TownsteadRootView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.quest.situation.SituationDetectors;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.TownsteadEventSignals;
import dev.otectus.mcaquests.quest.situation.TownsteadSituationDetector;
import dev.otectus.mcaquests.quest.situation.VillagePlace;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Subscribes to Townstead's events and hands each one, unpacked into primitives, to
 * {@link TownsteadEventSignals}, which decides whether it is news. Nothing about baselines or
 * deduplication lives here; this class only knows the API's record shapes and the gates.
 *
 * <h2>Gates</h2>
 *
 * <p>An event is dropped, and <em>nothing is recorded</em>, unless the scan would have observed the
 * same moment: situations on, Townstead content on, the bridge available with the capability the
 * scan requires for that signal, and a loaded definition that wants it. The scan does not seed
 * baselines it is not asked for, and neither does this.
 *
 * <h2>Where an event belongs</h2>
 *
 * <p>A villager's event is filed under their MCA home village, resolved the way the scan resolves
 * residents, so a villager with no home village is no village's news on either path. Village and
 * calendar events name their village and dimension themselves.
 *
 * <h2>Lifetime and thread</h2>
 *
 * <p>Townstead keeps its listener list for the JVM, not per server session, so the subscriptions
 * are made once when the bridge binds and released only if binding fails; every handler takes its
 * server from the event it received, which is what makes an integrated server's world switch
 * safe. The API posts on the server thread after the change has committed; any event arriving
 * from elsewhere is hopped back onto it before anything is read or written.
 */
final class ApiTownsteadEvents {

    private static final List<Subscription> SUBSCRIPTIONS = new ArrayList<>();

    private ApiTownsteadEvents() {
    }

    /**
     * Subscribes once. If any subscription throws, every one made so far is closed before the
     * failure propagates, so a bridge that then goes disabled leaves no listener behind.
     */
    static synchronized void start(TownsteadApiV1 api) {
        if (!SUBSCRIPTIONS.isEmpty()) {
            return;
        }
        try {
            EventsApi events = api.events();
            subscribe(events, VillagerCollapsedEvent.class, e -> onCollapsed(e));
            subscribe(events, VillagerRecoveredEvent.class, e -> onRecovered(e));
            subscribe(events, VillagerDiedEvent.class, e -> onDied(e));
            subscribe(events, VillagerTierChangedEvent.class, e -> onTierChanged(e));
            subscribe(events, VillagerLifeStageChangedEvent.class, e -> onLifeStage(api, e));
            subscribe(events, VillageSpiritChangedEvent.class, e -> onSpiritChanged(e));
            subscribe(events, BuildingEstablishedEvent.class,
                    e -> onBuilding(e.level(), e.village().villageId(), e.family(), e.tier()));
            subscribe(events, BuildingUpgradedEvent.class,
                    e -> onBuilding(e.level(), e.village().villageId(), ApiTownsteadBridge.family(e.typeAfter()), e.tierAfter()));
            subscribe(events, CalendarRolloverEvent.class, e -> onCalendar(e));
        } catch (Throwable t) {
            stop();
            throw t;
        }
        McaQuests.LOGGER.info("[MCA: Quests] Townstead events wired: {} subscriptions report transitions the "
                + "moment they happen; the scans stay on for what has no event.", SUBSCRIPTIONS.size());
    }

    /** Closes every subscription. Idempotent. */
    static synchronized void stop() {
        for (Subscription subscription : SUBSCRIPTIONS) {
            try {
                subscription.close();
            } catch (Throwable ignored) {
                // Closing is contractually idempotent and never throws; belt and braces.
            }
        }
        SUBSCRIPTIONS.clear();
    }

    static synchronized int activeSubscriptions() {
        int active = 0;
        for (Subscription subscription : SUBSCRIPTIONS) {
            if (subscription.isActive()) {
                active++;
            }
        }
        return active;
    }

    private static <E extends TownsteadEvent> void subscribe(EventsApi events, Class<E> type, Consumer<E> handler) {
        SUBSCRIPTIONS.add(events.subscribe(type, guarded(type, handler)));
    }

    /**
     * The listener actually registered: a record accessor that moved raises a {@link LinkageError}
     * inside the handler, which is named once and swallowed, so that event kind is simply not news
     * until the mod is rebuilt. Townstead's dispatcher would swallow it anyway; the scans still cover
     * the moment.
     */
    static <E extends TownsteadEvent> Consumer<E> guarded(Class<E> type, Consumer<E> handler) {
        return event -> {
            try {
                handler.accept(event);
            } catch (LinkageError drift) {
                ApiTownsteadBridge.reportDrift("event " + type.getSimpleName(), drift);
            }
        };
    }

    // --- villagers -------------------------------------------------------------------------------

    private static void onCollapsed(VillagerCollapsedEvent e) {
        Placed placed = place(e.villager());
        if (placed == null) {
            return;
        }
        onServerThread(placed.server(), () -> {
            if (open(SituationSignalType.TOWNSTEAD_COLLAPSE, TownsteadCapability.READ_VILLAGER)) {
                signals(placed.server()).collapsed(placed.level(), placed.villageId(), e.uuid());
            }
        });
    }

    private static void onRecovered(VillagerRecoveredEvent e) {
        MinecraftServer server = serverOf(e.villager());
        if (server == null) {
            return;
        }
        onServerThread(server, () -> {
            if (open(SituationSignalType.TOWNSTEAD_COLLAPSE, TownsteadCapability.READ_VILLAGER)) {
                signals(server).recovered(e.uuid());
            }
        });
    }

    private static void onDied(VillagerDiedEvent e) {
        MinecraftServer server = serverOf(e.villager());
        if (server == null) {
            return;
        }
        onServerThread(server, () -> {
            if (baseGatesOpen()) {
                signals(server).died(e.uuid());
            }
        });
    }

    private static void onTierChanged(VillagerTierChangedEvent e) {
        Placed placed = place(e.worker());
        if (placed == null) {
            return;
        }
        onServerThread(placed.server(), () -> {
            if (open(SituationSignalType.TOWNSTEAD_PROFESSION_TIER, TownsteadCapability.READ_VILLAGER)) {
                signals(placed.server()).tierChanged(placed.level(), placed.villageId(), e.uuid(), e.professionId(),
                        e.tierBefore(), e.tierAfter());
            }
        });
    }

    private static void onLifeStage(TownsteadApiV1 api, VillagerLifeStageChangedEvent e) {
        Placed placed = place(e.villager());
        if (placed == null) {
            return;
        }
        onServerThread(placed.server(), () -> {
            if (!open(SituationSignalType.TOWNSTEAD_LIFE_TRANSITION, TownsteadCapability.READ_VILLAGER)) {
                return;
            }
            // Both canonical stages come from the villager's root definition, resolved through the
            // bridge the way the scan resolves them, so a root that calls its adult stage "butterfly"
            // still yields the semantic child-to-adult crossing.
            TownsteadEvaluation evaluation = new TownsteadEvaluation();
            String rootId = evaluation.villager(e.villager()).map(TownsteadVillagerView::rootId).orElse("");
            String canonicalBefore = "";
            String canonicalAfter = "";
            if (!rootId.isEmpty() && TownsteadEvaluation.has(TownsteadCapability.READ_ROOT)) {
                ResourceLocation root = ResourceLocation.tryParse(rootId);
                Optional<TownsteadRootView> definition = root == null ? Optional.empty() : evaluation.root(root);
                if (definition.isPresent()) {
                    canonicalBefore = presentsAs(definition.get(), e.stageBefore());
                    canonicalAfter = presentsAs(definition.get(), e.stageAfter());
                }
            }
            signals(placed.server()).lifeStageChanged(placed.level(), placed.villageId(), e.uuid(), e.stageBefore(),
                    e.stageAfter(), e.senior(), canonicalBefore, canonicalAfter);
        });
    }

    private static String presentsAs(TownsteadRootView root, String stageId) {
        if (stageId == null || stageId.isEmpty()) {
            return "";
        }
        for (TownsteadLifeStageView stage : root.lifeStages()) {
            if (stage.id().equalsIgnoreCase(stageId)) {
                return stage.presentsAs();
            }
        }
        return "";
    }

    // --- villages --------------------------------------------------------------------------------

    private static void onSpiritChanged(VillageSpiritChangedEvent e) {
        ServerLevel level = e.level();
        if (level == null || level.getServer() == null || e.after() == null) {
            return;
        }
        onServerThread(level.getServer(), () -> {
            if (!open(SituationSignalType.TOWNSTEAD_SPIRIT, TownsteadCapability.READ_SPIRIT)) {
                return;
            }
            int villageId = e.village().villageId();
            int tierBefore = e.before() == null ? e.after().tierIndex() : e.before().tierIndex();
            String primaryBefore = e.before() == null ? "" : e.before().primarySpiritId().orElse("");
            String classBefore = e.before() == null ? "" : e.before().classification();
            signals(level.getServer()).spiritChanged(level, villageId, tierBefore, e.after().tierIndex(),
                    primaryBefore, e.after().primarySpiritId().orElse(""), classBefore, e.after().classification());
        });
    }

    private static void onBuilding(ServerLevel level, int villageId, String family, int tier) {
        if (level == null || level.getServer() == null) {
            return;
        }
        onServerThread(level.getServer(), () -> {
            if (!open(SituationSignalType.TOWNSTEAD_BUILDING, TownsteadCapability.READ_BUILDING)) {
                return;
            }
            OptionalInt signature = TownsteadSituationDetector.currentRegisterSignature(level, villageId);
            signals(level.getServer()).building(level, villageId, signature, family == null ? "" : family, tier);
        });
    }

    private static void onCalendar(CalendarRolloverEvent e) {
        // Every period is derived from the day, and the pure side compares before and after for each
        // one, so the DAY event alone carries the week, month, season and year crossings.
        if (e.kind() != CalendarRolloverEvent.Kind.DAY || e.server() == null) {
            return;
        }
        MinecraftServer server = e.server();
        onServerThread(server, () -> {
            if (!open(SituationSignalType.TOWNSTEAD_CALENDAR_TRANSITION, TownsteadCapability.READ_CALENDAR)) {
                return;
            }
            List<VillagePlace> villages = SituationDetectors.villagesNearPlayers(server);
            if (villages.isEmpty()) {
                return;
            }
            signals(server).dayRolledOver(
                    e.before() == null ? null : ApiTownsteadBridge.calendarView(e.before()),
                    ApiTownsteadBridge.calendarView(e.after()), villages);
        });
    }

    // --- plumbing --------------------------------------------------------------------------------

    private record Placed(ServerLevel level, int villageId) {
        MinecraftServer server() {
            return level.getServer();
        }
    }

    /** The villager's home village, by MCA's own residency, as the scan files residents. */
    private static Placed place(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || level.getServer() == null) {
            return null;
        }
        OptionalInt home = McaCompat.getHomeVillageId(entity);
        return home.isPresent() ? new Placed(level, home.getAsInt()) : null;
    }

    private static MinecraftServer serverOf(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level ? level.getServer() : null;
    }

    private static boolean baseGatesOpen() {
        return McaQuestsConfig.COMMON.townsteadEnabled.get()
                && McaQuestsConfig.COMMON.enableSituations.get()
                && McaQuestsConfig.COMMON.townsteadContentEnabled.get()
                && TownsteadBridge.Holder.get().isAvailable();
    }

    private static boolean open(SituationSignalType type, TownsteadCapability capability) {
        return baseGatesOpen()
                && TownsteadBridge.Holder.get().has(capability)
                && TownsteadSituationDetector.wants(type);
    }

    private static TownsteadEventSignals signals(MinecraftServer server) {
        return new TownsteadEventSignals(TownsteadSignalStateSavedData.get(server),
                TownsteadSituationDetector::wants, signal -> SituationManager.onSignal(server, signal));
    }

    private static void onServerThread(MinecraftServer server, Runnable body) {
        if (server.isSameThread()) {
            body.run();
        } else {
            server.execute(body);
        }
    }
}
