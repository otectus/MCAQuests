package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalRef;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.InterregnumView;
import dev.otectus.mcaquests.quest.situation.state.CapitalsSignalStateSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns MCA Capitals court state into situation signals (1.6.0).
 *
 * <h2>Why this polls instead of listening</h2>
 *
 * <p>Capitals settles a succession from its own {@code LivingDeathEvent} handler, on the same bus at the
 * same priority as ours, and Forge gives no ordering between two such listeners. An event-driven
 * detector would therefore read the throne <em>before</em> Capitals had vacated it about half the time,
 * and which half would depend on mod load order. A poll asks a question that has already been settled,
 * at the cost of up to {@code compat.capitals.pollIntervalTicks} of latency.
 *
 * <p>Everything here compares against the last reading, held in {@link CapitalsSignalStateSavedData}, so
 * a throne that is still empty is not news and a war that is still being fought is not declared again.
 * A first observation seeds the baseline and stays silent, which is what stops installing this on an
 * existing world from opening a situation for every vacancy and every war already in it.
 *
 * <p>Nothing runs at all unless some loaded definition actually consumes the signal, so a pack with no
 * court situations pays one registry scan per poll and nothing else — and none of it runs when Capitals
 * is absent, switched off, or bound to a build the manifest does not understand.
 */
public final class CapitalsSituationDetector {

    /** The diplomatic state name that is worth a signal. Compared case-insensitively. */
    private static final String WAR = "WAR";

    private CapitalsSituationDetector() {
    }

    /**
     * One pass over every capital Capitals knows about, called from the throttled server tick.
     *
     * <p>Throwable-safe as a whole: every read below goes through a reflective bridge, so a Capitals
     * build that renamed something under us must degrade to "no court situations" rather than take the
     * server tick down with it.
     */
    public static void poll(MinecraftServer server) {
        try {
            if (!McaQuestsConfig.COMMON.enableSituations.get()) {
                return;
            }
            CapitalsBridge bridge = CapitalsCompat.bridge();
            CompatStatus status = bridge.status();
            if (status != CompatStatus.PARTIAL && status != CompatStatus.FULL) {
                return;
            }
            boolean wantsInterregnum = wants(SituationSignalType.CAPITAL_INTERREGNUM)
                    && bridge.has(CapitalsCapability.INTERREGNUM);
            boolean wantsWar = wants(SituationSignalType.CAPITAL_WAR)
                    && bridge.has(CapitalsCapability.DIPLOMACY);
            if (!wantsInterregnum && !wantsWar) {
                return;
            }

            List<Seat> seats = seats(server, bridge);
            if (seats.isEmpty()) {
                return;
            }
            CapitalsSignalStateSavedData state = CapitalsSignalStateSavedData.get(server);
            if (wantsInterregnum) {
                scanThrones(server, bridge, state, seats);
            }
            if (wantsWar) {
                scanRelations(server, bridge, state, seats);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Capitals situation poll failed", t);
        }
    }

    /** True when some loaded definition actually consumes this signal. */
    private static boolean wants(SituationSignalType type) {
        return SituationRegistry.all().stream()
                .filter(SituationDefinition::enabled)
                .anyMatch(def -> def.trigger().signalType() == type);
    }

    /**
     * The capitals worth asking about, each paired with the level it sits in.
     *
     * <p>A capital is skipped when its level cannot be resolved (its dimension is gone), when it is not
     * active — Capitals also carries pending and founded ones, which have no court yet — or when the MCA
     * village behind it no longer exists, because a signal scoped to a village that is not there can
     * never be offered to anybody.
     */
    private static List<Seat> seats(MinecraftServer server, CapitalsBridge bridge) {
        List<Seat> seats = new ArrayList<>();
        for (CapitalRef capital : bridge.allCapitals()) {
            ServerLevel level = bridge.capitalLevel(server, capital).orElse(null);
            if (level == null || !bridge.isActive(capital)
                    || !McaCompat.villageExists(level, capital.villageId())) {
                continue;
            }
            seats.add(new Seat(capital, level));
        }
        return seats;
    }

    // ------------------------------------------------------------------------------ interregnum

    /**
     * Raises {@code capital_interregnum} on the crossing into a vacant throne.
     *
     * <p>The snapshot is taken once per level rather than once per capital: Capitals keeps every
     * interregnum in one store, and asking per capital would re-read the same map for every court in the
     * dimension.
     */
    private static void scanThrones(MinecraftServer server, CapitalsBridge bridge,
                                    CapitalsSignalStateSavedData state, List<Seat> seats) {
        Map<ServerLevel, Map<UUID, InterregnumView>> snapshots = new HashMap<>();
        for (Seat seat : seats) {
            Map<UUID, InterregnumView> vacancies =
                    snapshots.computeIfAbsent(seat.level(), bridge::interregnums);
            InterregnumView view = vacancies.get(seat.capital().capitalId());
            if (!state.observeRisingEdge(seat.capital().capitalId() + "|interregnum", view != null)) {
                continue;
            }
            SituationManager.onSignal(server, TriggerSignal.capitalInterregnum(
                    seat.level(), seat.capital().villageId(),
                    view.deceasedSovereign(), view.wasPlayerSovereign()));
        }
    }

    // -------------------------------------------------------------------------------------- war

    /**
     * Raises {@code capital_war} on the crossing of a pair of capitals into war.
     *
     * <p>The pair is keyed unordered, because "A declared on B" and "B declared on A" are one war and
     * keying it either way round would remember two baselines and announce the same war twice. Both
     * capitals still get their own signal — a war is news in both villages — but the two come from one
     * observed transition, and each carries the relation that transition came out of.
     */
    private static void scanRelations(MinecraftServer server, CapitalsBridge bridge,
                                      CapitalsSignalStateSavedData state, List<Seat> seats) {
        for (int i = 0; i < seats.size(); i++) {
            for (int j = i + 1; j < seats.size(); j++) {
                Seat first = seats.get(i);
                Seat second = seats.get(j);
                UUID a = first.capital().capitalId();
                UUID b = second.capital().capitalId();
                // An unreadable relation stores nothing, so the next readable one is not a transition
                // out of nowhere.
                String current = bridge.diplomaticState(first.level(), a, b).orElse("");
                Optional<String> previous = state.observeLabel(relationKey(a, b), current);
                if (previous.isEmpty() || !current.equalsIgnoreCase(WAR)) {
                    continue;
                }
                String from = previous.get();
                SituationManager.onSignal(server, TriggerSignal.capitalWar(
                        first.level(), first.capital().villageId(), b, from));
                SituationManager.onSignal(server, TriggerSignal.capitalWar(
                        second.level(), second.capital().villageId(), a, from));
            }
        }
    }

    /**
     * The persisted key for a pair of capitals, independent of which of them declared.
     *
     * <p>Public and pure so the ordering rule can be tested without a server: getting it wrong is
     * invisible until the day two capitals go to war and the situation opens twice.
     */
    public static String relationKey(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0
                ? "relation|" + first + "|" + second
                : "relation|" + second + "|" + first;
    }

    /** A live capital and the level it sits in, resolved once per poll. */
    private record Seat(CapitalRef capital, ServerLevel level) {
    }
}
