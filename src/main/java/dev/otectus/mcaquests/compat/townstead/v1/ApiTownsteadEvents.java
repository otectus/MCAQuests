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
import com.aetherianartificer.townstead.api.v1.event.VillagerLifeStageChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerRecoveredEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerTierChangedEvent;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.compat.TownsteadPeriod;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Townstead's events, turned into the same situation signals the polling detector produces, at the
 * moment they happen rather than on the next scan.
 *
 * <p>Every handler records its observation in {@link TownsteadSignalStateSavedData} under the exact
 * key the scan uses, so a scan that runs afterwards finds the baseline already moved and stays
 * silent: one moment, one signal, whichever path noticed it first. The scans keep running for
 * everything that has no event (need crises, schedule streaks) and as the safety net.
 */
final class ApiTownsteadEvents {

    private static final List<Subscription> SUBSCRIPTIONS = new ArrayList<>();

    private ApiTownsteadEvents() {
    }

    static synchronized void start(TownsteadApiV1 api) {
        if (!SUBSCRIPTIONS.isEmpty()) {
            return;
        }
        EventsApi events = api.events();
        subscribe(events, VillagerCollapsedEvent.class, e -> onCollapsed(api, e));
        subscribe(events, VillagerRecoveredEvent.class, e -> onRecovered(e));
        subscribe(events, VillagerTierChangedEvent.class, e -> onTierChanged(api, e));
        subscribe(events, VillageSpiritChangedEvent.class, e -> onSpiritChanged(e));
        subscribe(events, BuildingEstablishedEvent.class, e -> onBuilding(e.level(), e.village(), e.family(), e.tier()));
        subscribe(events, BuildingUpgradedEvent.class,
                e -> onBuilding(e.level(), e.village(), ApiTownsteadBridge.family(e.typeAfter()), e.tierAfter()));
        subscribe(events, CalendarRolloverEvent.class, e -> onCalendar(api, e));
        subscribe(events, VillagerLifeStageChangedEvent.class, e -> onLifeStage(api, e));
        McaQuests.LOGGER.info("[MCA: Quests] Townstead events wired: {} subscriptions replace the matching scans.",
                SUBSCRIPTIONS.size());
    }

    static synchronized void stop() {
        for (Subscription subscription : SUBSCRIPTIONS) {
            subscription.close();
        }
        SUBSCRIPTIONS.clear();
    }

    private static <E extends TownsteadEvent> void subscribe(EventsApi events, Class<E> type, Consumer<E> handler) {
        SUBSCRIPTIONS.add(events.subscribe(type, event -> {
            if (!McaQuestsConfig.COMMON.townsteadEnabled.get()) {
                return;
            }
            handler.accept(event);
        }));
    }

    // --- villagers -------------------------------------------------------------------------------

    private static void onCollapsed(TownsteadApiV1 api, VillagerCollapsedEvent e) {
        Placed placed = place(api, e.villager());
        if (placed == null) {
            return;
        }
        TownsteadSignalStateSavedData state = state(placed);
        if (state.observeRisingEdge(e.uuid() + "|collapsed", true)) {
            fire(placed, TriggerSignal.townsteadCollapse(placed.level(), placed.villageId(), e.uuid()));
        }
    }

    private static void onRecovered(VillagerRecoveredEvent e) {
        if (!(e.villager().level() instanceof ServerLevel level)) {
            return;
        }
        // Lower the edge so the next collapse is a rise again; the scan would do the same.
        TownsteadSignalStateSavedData.get(level.getServer()).observeRisingEdge(e.uuid() + "|collapsed", false);
    }

    private static void onTierChanged(TownsteadApiV1 api, VillagerTierChangedEvent e) {
        Placed placed = place(api, e.worker());
        if (placed == null) {
            return;
        }
        TownsteadSignalStateSavedData state = state(placed);
        String key = e.uuid() + "|tier|" + e.professionId();
        int previous = state.lastReading(key, e.tierBefore());
        if (state.observeIncrease(key, e.tierAfter())) {
            fire(placed, TriggerSignal.townsteadProfessionTier(placed.level(), placed.villageId(), e.uuid(),
                    e.professionId(), previous, e.tierAfter()));
        }
    }

    private static void onLifeStage(TownsteadApiV1 api, VillagerLifeStageChangedEvent e) {
        Placed placed = place(api, e.villager());
        if (placed == null) {
            return;
        }
        TownsteadSignalStateSavedData state = state(placed);
        String uuid = e.uuid().toString();
        life(placed, state, e, "senior", uuid + "|senior", String.valueOf(e.senior()));
        life(placed, state, e, "life_stage", uuid + "|life_stage", e.stageAfter());
        Optional<VillagerSnapshot> snapshot = api.villagers().snapshot(e.villager());
        String rootId = snapshot.map(VillagerSnapshot::rootId).orElse("");
        ResourceLocation root = rootId.isEmpty() ? null : ResourceLocation.tryParse(rootId);
        if (root != null) {
            Optional<RootSnapshot> definition = api.social().root(root);
            if (definition.isPresent()) {
                for (RootSnapshot.LifeStageInfo stage : definition.get().lifeStages()) {
                    if (stage.id().equalsIgnoreCase(e.stageAfter())) {
                        life(placed, state, e, "canonical_stage", uuid + "|canonical_stage", stage.presentsAs());
                        break;
                    }
                }
            }
        }
    }

    private static void life(Placed placed, TownsteadSignalStateSavedData state, VillagerLifeStageChangedEvent e,
                             String axis, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        state.observeLabel(key, value).ifPresent(previous -> fire(placed, TriggerSignal.townsteadLifeTransition(
                placed.level(), placed.villageId(), e.uuid(), axis, previous, value)));
    }

    // --- villages --------------------------------------------------------------------------------

    private static void onSpiritChanged(VillageSpiritChangedEvent e) {
        Placed placed = new Placed(e.level(), e.village().villageId());
        TownsteadSignalStateSavedData state = state(placed);
        int villageId = placed.villageId();
        String tierKey = villageId + "|spirit";
        int previous = state.lastReading(tierKey, e.after().tierIndex());
        boolean roseATier = state.observeIncrease(tierKey, e.after().tierIndex());
        String primary = e.after().primarySpiritId().orElse("");
        boolean changedIdentity = state.observeChanged(villageId + "|spirit_id", primary.hashCode());
        String previousClassification =
                state.observeLabel(villageId + "|spirit_class", e.after().classification()).orElse(null);
        if (roseATier || changedIdentity || previousClassification != null) {
            fire(placed, TriggerSignal.townsteadSpirit(placed.level(), villageId, primary, previous,
                    e.after().tierIndex(), previousClassification, e.after().classification()));
        }
    }

    private static void onBuilding(ServerLevel level, VillageId village, String family, int tier) {
        Placed placed = new Placed(level, village.villageId());
        // The scan keys the whole register's signature; an event knows only the one building, so it
        // fires directly and drops the signature. The next scan re-seeds it silently.
        state(placed).forget(village.villageId() + "|buildings");
        fire(placed, TriggerSignal.townsteadBuilding(level, village.villageId(), family, tier));
    }

    private static void onCalendar(TownsteadApiV1 api, CalendarRolloverEvent e) {
        if (e.kind() != CalendarRolloverEvent.Kind.DAY) {
            return; // the period values below are derived from the day; one pass per day is enough
        }
        MinecraftServer server = e.server();
        TownsteadCalendarView calendar = calendarView(e.after());
        if (calendar.profileId().isEmpty()) {
            return;
        }
        TownsteadSignalStateSavedData state = TownsteadSignalStateSavedData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (VillageSnapshot village : api.villages().all(level)) {
                Placed placed = new Placed(level, village.id().villageId());
                for (TownsteadPeriod period : TownsteadPeriod.values()) {
                    String value = period.currentValue(calendar);
                    if (value.isEmpty()) {
                        continue;
                    }
                    String key = "calendar|" + calendar.profileId() + '|' + period.id() + '|' + placed.villageId();
                    state.observeLabel(key, value).ifPresent(previous -> fire(placed,
                            TriggerSignal.townsteadCalendarTransition(level, placed.villageId(), period.id(),
                                    previous, value)));
                }
            }
        }
    }

    // --- plumbing --------------------------------------------------------------------------------

    private record Placed(ServerLevel level, int villageId) {
    }

    private static Placed place(TownsteadApiV1 api, LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        Optional<VillageId> home = api.villages().homeOf(entity);
        return home.map(id -> new Placed(level, id.villageId())).orElse(null);
    }

    private static TownsteadSignalStateSavedData state(Placed placed) {
        return TownsteadSignalStateSavedData.get(placed.level().getServer());
    }

    private static void fire(Placed placed, TriggerSignal signal) {
        SituationManager.onSignal(placed.level().getServer(), signal);
        TownsteadCounters.signalFired();
    }

    static TownsteadCalendarView calendarView(CalendarSnapshot c) {
        return new TownsteadCalendarView(c.profileId(), c.worldDay(), c.epochYearOffset(), c.timeMode(), c.year(),
                c.month(), c.day(), c.dayOfYear(), c.dayOfWeek(), c.season());
    }
}
