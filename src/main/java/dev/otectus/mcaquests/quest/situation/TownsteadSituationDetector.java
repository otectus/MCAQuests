package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadLifeStageView;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadPeriod;
import dev.otectus.mcaquests.compat.TownsteadRootView;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.compat.TownsteadVillageBuilding;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns Townstead village state into situation signals (Townstead spec §7.3).
 *
 * <p>Three rules shape all of it, and they are all about <b>not crying wolf</b>.
 *
 * <ol>
 *   <li><b>Transitions, not states.</b> Everything here compares against the last observation, held in
 *       {@link TownsteadSignalStateSavedData}. A villager who is still collapsed is not news; the
 *       moment they collapsed was. Without that, a village in trouble would emit the same signal every
 *       scan forever.</li>
 *   <li><b>Hysteresis on crises.</b> A need crisis opens at one threshold and closes at a higher one,
 *       so a villager hovering on the boundary cannot flap a situation on and off every few seconds.
 *       The gap is {@code compat.townstead.needCrisisHysteresis}, read as a percentage of the need's
 *       own range — the ranges differ (hunger runs to 100, thirst and energy to 20), so a flat number
 *       would be a rounding error on one and half the scale on another.</li>
 *   <li><b>Budgets.</b> Villages and residents are both capped per pass and visited round-robin, so a
 *       large world costs a bounded amount per scan and nobody is skipped forever.</li>
 * </ol>
 *
 * <p>Nothing here runs at all unless a loaded definition actually wants the signal in question, so a
 * pack with no building situations pays nothing for building detection.
 */
public final class TownsteadSituationDetector {

    /** Fractions of each need's own range at which a villager counts as in crisis. */
    private static final double HUNGER_CRISIS = 0.20D;
    private static final double THIRST_CRISIS = 0.25D;
    private static final double ENERGY_CRISIS = 0.25D;

    /** How much of a village must be suffering before it is the village that is in trouble. */
    private static final double CRISIS_FRACTION = 0.34D;

    /**
     * The schedule-disruption thresholds (spec 10.2). The recovery gap is deliberately wider than the
     * need hysteresis: a routine recovers gradually, and re-arming the moment it dipped back under the
     * line would open a second situation while the player was still resolving the first.
     */
    private static final int DISRUPTION_MINIMUM_OBSERVED = 5;
    private static final double DISRUPTION_FRACTION = 0.50D;
    private static final double DISRUPTION_RECOVERY_GAP = 0.15D;
    private static final long DISRUPTION_HOLD_TICKS = 1200L;

    private TownsteadSituationDetector() {
    }

    /**
     * Scans one village. Called from {@code SituationDetectors.scanVillage}, so this piggybacks on the
     * existing sweep rather than adding a second one.
     *
     * @param rotation a value that changes between passes, so a village larger than the resident cap is
     *                 walked from a different offset each time
     */
    public static void scanVillage(MinecraftServer server, ServerLevel level, int villageId, long rotation) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable() || !McaQuestsConfig.COMMON.townsteadContentEnabled.get()) {
            return;
        }
        long startedAt = System.nanoTime();
        TownsteadSignalStateSavedData state = TownsteadSignalStateSavedData.get(server);
        TownsteadEvaluation evaluation = new TownsteadEvaluation();

        boolean wantsNeeds = wants(SituationSignalType.TOWNSTEAD_NEED);
        boolean wantsCollapse = wants(SituationSignalType.TOWNSTEAD_COLLAPSE);
        boolean wantsTiers = wants(SituationSignalType.TOWNSTEAD_PROFESSION_TIER);
        boolean wantsLife = wants(SituationSignalType.TOWNSTEAD_LIFE_TRANSITION);
        if ((wantsNeeds || wantsCollapse || wantsTiers || wantsLife)
                && bridge.has(TownsteadCapability.READ_VILLAGER)) {
            scanResidents(server, level, villageId, rotation, state, evaluation,
                    wantsNeeds, wantsCollapse, wantsTiers, wantsLife);
        }
        if (wants(SituationSignalType.TOWNSTEAD_SPIRIT) && bridge.has(TownsteadCapability.READ_SPIRIT)) {
            scanSpirit(server, level, villageId, state, evaluation);
        }
        if (wants(SituationSignalType.TOWNSTEAD_BUILDING) && bridge.has(TownsteadCapability.READ_BUILDING)) {
            scanBuildings(server, level, villageId, state, evaluation);
        }
        if (wants(SituationSignalType.TOWNSTEAD_CALENDAR_TRANSITION)
                && bridge.has(TownsteadCapability.READ_CALENDAR)) {
            scanCalendar(server, level, villageId, state, evaluation);
        }
        if (wants(SituationSignalType.TOWNSTEAD_SCHEDULE_DISRUPTION)
                && bridge.has(TownsteadCapability.READ_SCHEDULE)) {
            scanSchedules(server, level, villageId, rotation, state, evaluation);
        }
        TownsteadCounters.villageScanned(System.nanoTime() - startedAt);
    }

    /** True when some loaded definition actually consumes this signal. */
    private static boolean wants(SituationSignalType type) {
        return SituationRegistry.all().stream()
                .filter(SituationDefinition::enabled)
                .anyMatch(def -> def.trigger().signalType() == type);
    }

    // ------------------------------------------------------------------------------- residents

    private static void scanResidents(MinecraftServer server, ServerLevel level, int villageId,
                                      long rotation, TownsteadSignalStateSavedData state,
                                      TownsteadEvaluation evaluation, boolean wantsNeeds,
                                      boolean wantsCollapse, boolean wantsTiers, boolean wantsLife) {
        List<Entity> residents = window(McaCompat.loadedVillageResidents(level, villageId), rotation);
        if (residents.isEmpty()) {
            return;
        }

        int observed = 0;
        int hungry = 0;
        int thirsty = 0;
        int weary = 0;
        double worstHunger = TownsteadNeedsView.HUNGER_MAX;
        double worstThirst = TownsteadNeedsView.THIRST_MAX;
        double worstEnergy = TownsteadNeedsView.FATIGUE_MAX;

        for (Entity resident : residents) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view == null) {
                continue;
            }
            observed++;
            TownsteadCounters.residentObserved();
            TownsteadNeedsView needs = view.needs();

            if (wantsNeeds) {
                if (needs.hunger() <= TownsteadNeedsView.HUNGER_MAX * HUNGER_CRISIS) {
                    hungry++;
                    worstHunger = Math.min(worstHunger, needs.hunger());
                }
                if (needs.thirstActive() && needs.thirst() <= TownsteadNeedsView.THIRST_MAX * THIRST_CRISIS) {
                    thirsty++;
                    worstThirst = Math.min(worstThirst, needs.thirst());
                }
                if (needs.energy() <= TownsteadNeedsView.FATIGUE_MAX * ENERGY_CRISIS) {
                    weary++;
                    worstEnergy = Math.min(worstEnergy, needs.energy());
                }
            }
            if (wantsCollapse && state.observeRisingEdge(
                    resident.getUUID() + "|collapsed", needs.collapsed())) {
                SituationManager.onSignal(server,
                        TriggerSignal.townsteadCollapse(level, villageId, resident.getUUID()));
                    TownsteadCounters.signalFired();
            }
            if (wantsLife) {
                scanLife(server, level, villageId, state, evaluation, resident, view);
            }
            if (wantsTiers && view.hasProfession()) {
                String key = resident.getUUID() + "|tier|" + view.professionId();
                int previous = state.lastReading(key, view.professionLevel());
                if (state.observeIncrease(key, view.professionLevel())) {
                    SituationManager.onSignal(server, TriggerSignal.townsteadProfessionTier(level, villageId,
                            resident.getUUID(), view.professionId(), previous, view.professionLevel()));
                    TownsteadCounters.signalFired();
                }
            }
        }

        if (!wantsNeeds || observed == 0) {
            return;
        }
        crisis(server, level, villageId, state, "hunger", hungry, observed, worstHunger,
                TownsteadNeedsView.HUNGER_MAX);
        crisis(server, level, villageId, state, "thirst", thirsty, observed, worstThirst,
                TownsteadNeedsView.THIRST_MAX);
        crisis(server, level, villageId, state, "energy", weary, observed, worstEnergy,
                TownsteadNeedsView.FATIGUE_MAX);
    }

    /**
     * Opens or closes a village-wide need crisis, with the hysteresis gap between the two thresholds.
     * Only the opening is a signal; closing simply clears the flag so the next slide can be announced.
     */
    private static void crisis(MinecraftServer server, ServerLevel level, int villageId,
                               TownsteadSignalStateSavedData state, String need, int suffering,
                               int observed, double worst, int needMax) {
        double fraction = (double) suffering / observed;
        String key = villageId + "|need|" + need;
        boolean wasInCrisis = state.lastReading(key, 0) == 1;

        double hysteresis = McaQuestsConfig.COMMON.townsteadNeedCrisisHysteresis.get() / 100.0D;
        boolean inCrisis = inCrisis(wasInCrisis, fraction, CRISIS_FRACTION, hysteresis);

        if (state.observeChanged(key, inCrisis ? 1 : 0) && inCrisis) {
            SituationManager.onSignal(server, TriggerSignal.townsteadNeed(level, villageId, need,
                    (float) fraction, worst / Math.max(1, needMax)));
            TownsteadCounters.signalFired();
        }
    }

    /**
     * Whether a village counts as in crisis, given whether it already did.
     *
     * <p>Two thresholds, not one. A crisis opens when the suffering fraction reaches {@code enterAt},
     * and closes only once it falls back below {@code enterAt - hysteresis}. With a single threshold a
     * village sitting exactly on the line would open and close the same emergency every scan, which
     * reads to a player as a situation flickering in and out of their quest list.
     *
     * <p>Extracted and package-visible so the banding can be tested directly; it is otherwise buried
     * behind a live server and a saved-data store.
     */
    static boolean inCrisis(boolean wasInCrisis, double fraction, double enterAt, double hysteresis) {
        double leaveAt = Math.max(0.0D, enterAt - hysteresis);
        return wasInCrisis ? fraction > leaveAt : fraction >= enterAt;
    }

    // ---------------------------------------------------------------------------------- village

    private static void scanSpirit(MinecraftServer server, ServerLevel level, int villageId,
                                   TownsteadSignalStateSavedData state, TownsteadEvaluation evaluation) {
        TownsteadSpiritView view = evaluation.spirit(level, villageId).orElse(null);
        if (view == null) {
            return;
        }
        String tierKey = villageId + "|spirit";
        int previous = state.lastReading(tierKey, view.tier());
        boolean roseATier = state.observeIncrease(tierKey, view.tier());

        // An identity change matters even without a tier: a village that has become known for its docks
        // rather than its fields is news whether or not the number went up.
        String identityKey = villageId + "|spirit_id";
        boolean changedIdentity = state.observeChanged(identityKey, view.primaryId().hashCode());

        // The classification is a separate axis again -- settlement, a single name, blend, mixed -- and
        // it is the one a "what kind of place is this" situation is really about. Observed as a label
        // rather than a hash so the signal can say what it changed from.
        String previousClassification =
                state.observeLabel(villageId + "|spirit_class", view.classification()).orElse(null);

        if (roseATier || changedIdentity || previousClassification != null) {
            SituationManager.onSignal(server, TriggerSignal.townsteadSpirit(level, villageId,
                    view.primaryId(), previous, view.tier(),
                    previousClassification, view.classification()));
            TownsteadCounters.signalFired();
        }
    }

    private static void scanBuildings(MinecraftServer server, ServerLevel level, int villageId,
                                      TownsteadSignalStateSavedData state, TownsteadEvaluation evaluation) {
        List<TownsteadVillageBuilding> buildings = evaluation.buildingsIn(level, villageId);
        if (buildings.isEmpty()) {
            return;
        }
        // One number for the whole village -- every building family and its tier folded together -- so a
        // village of any size costs one stored reading rather than one per building.
        int signature = 0;
        TownsteadVillageBuilding newest = buildings.get(0);
        for (TownsteadVillageBuilding building : buildings) {
            signature = signature * 31 + building.family().hashCode() + building.level();
            if (building.id() > newest.id()) {
                newest = building; // MCA hands out rising ids, so the highest is the most recent
            }
        }
        if (state.observeChanged(villageId + "|buildings", signature)) {
            SituationManager.onSignal(server, TriggerSignal.townsteadBuilding(level, villageId,
                    newest.family(), newest.level()));
            TownsteadCounters.signalFired();
        }
    }

    // ------------------------------------------------------------------------------ transitions

    /**
     * Fires when the calendar turns over a week, a season or a year (spec 5.8).
     *
     * <p>Baselines are keyed by the calendar <b>profile</b> as well as the period, so switching
     * profiles mid-world seeds a fresh baseline instead of synthesising a season change out of two
     * incomparable calendars. Each period is observed independently: a year rolling over is genuinely
     * a different event from a season doing so, even though they happen on the same day.
     *
     * <p>The signal is emitted per village because situations are village-scoped, but the reading is
     * server-wide, so the baseline is keyed on the profile rather than on the village -- otherwise the
     * first village scanned after a season change would fire and the rest would find the baseline
     * already updated and stay silent.
     */
    private static void scanCalendar(MinecraftServer server, ServerLevel level, int villageId,
                                     TownsteadSignalStateSavedData state, TownsteadEvaluation evaluation) {
        TownsteadCalendarView calendar = evaluation.calendar(server).orElse(null);
        if (calendar == null || calendar.profileId().isEmpty()) {
            return;
        }
        for (TownsteadPeriod period : TownsteadPeriod.values()) {
            String value = period.currentValue(calendar);
            if (value.isEmpty()) {
                continue; // a profile with no seasons simply has no season transitions
            }
            String key = "calendar|" + calendar.profileId() + '|' + period.id() + '|' + villageId;
            state.observeLabel(key, value).ifPresent(previous -> {
                SituationManager.onSignal(server, TriggerSignal.townsteadCalendarTransition(
                        level, villageId, period.id(), previous, value));
                TownsteadCounters.signalFired();
            });
        }
    }

    /**
     * Fires when a villager comes of age or becomes a senior (spec 5.8).
     *
     * <p>{@code canonical_stage} is resolved through the villager's root definition rather than read
     * off the stage id, because Townstead roots may name their stages anything they like. A root whose
     * adult stage is called "butterfly" still produces the semantic child-to-adult crossing, which is
     * what a coming-of-age story actually wants to know.
     *
     * <p>The raw stage id is observed as well, for a pack that means one specific stage of one specific
     * root; both are cheap, and offering only the semantic axis would make that pack impossible.
     */
    private static void scanLife(MinecraftServer server, ServerLevel level, int villageId,
                                 TownsteadSignalStateSavedData state, TownsteadEvaluation evaluation,
                                 Entity resident, TownsteadVillagerView view) {
        String uuid = resident.getUUID().toString();
        fireLife(server, level, villageId, state, resident, "senior",
                uuid + "|senior", String.valueOf(view.senior()));
        fireLife(server, level, villageId, state, resident, "life_stage",
                uuid + "|life_stage", view.lifeStage());
        String canonical = canonicalStage(evaluation, view);
        if (!canonical.isEmpty()) {
            fireLife(server, level, villageId, state, resident, "canonical_stage",
                    uuid + "|canonical_stage", canonical);
        }
    }

    private static void fireLife(MinecraftServer server, ServerLevel level, int villageId,
                                 TownsteadSignalStateSavedData state, Entity resident, String axis,
                                 String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        state.observeLabel(key, value).ifPresent(previous -> {
            SituationManager.onSignal(server, TriggerSignal.townsteadLifeTransition(
                    level, villageId, resident.getUUID(), axis, previous, value));
            TownsteadCounters.signalFired();
        });
    }

    /**
     * What this villager's current life stage <em>presents as</em>, per their root's own definition.
     * Empty when the root cannot be read, which means the semantic axis is simply not observed rather
     * than being guessed at from the stage's name.
     */
    private static String canonicalStage(TownsteadEvaluation evaluation, TownsteadVillagerView view) {
        if (view.lifeStage().isEmpty() || view.rootId().isEmpty()
                || !TownsteadEvaluation.has(TownsteadCapability.READ_ROOT)) {
            return "";
        }
        TownsteadRootView root = evaluation.root(ResourceLocation.tryParse(view.rootId())).orElse(null);
        if (root == null) {
            return "";
        }
        for (TownsteadLifeStageView stage : root.lifeStages()) {
            if (stage.id().equalsIgnoreCase(view.lifeStage())) {
                return stage.presentsAs();
            }
        }
        return "";
    }

    /**
     * Fires when a village has been off its own schedule for long enough to mean something (spec 5.8).
     *
     * <p>Three guards, and every one of them is there because the naive version cries wolf. Villagers
     * are routinely off schedule for a few seconds while walking between jobs, so the disruption has to
     * <b>persist</b>: the detector counts consecutive observations rather than firing on the first.
     * A claim about a village needs enough of the village <b>visible</b>. And once fired, it cannot
     * arm again until the fraction has fallen well below the one that opened it, so a village sitting
     * on the boundary does not flicker a situation in and out of the quest list.
     */
    private static void scanSchedules(MinecraftServer server, ServerLevel level, int villageId,
                                      long rotation, TownsteadSignalStateSavedData state,
                                      TownsteadEvaluation evaluation) {
        List<Entity> residents = window(McaCompat.loadedVillageResidents(level, villageId), rotation);
        int observed = 0;
        int adrift = 0;
        for (Entity resident : residents) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view == null) {
                continue;
            }
            observed++;
            if (!view.schedule().onSchedule()) {
                adrift++;
            }
        }
        String holdKey = villageId + "|schedule_hold";
        String firedKey = villageId + "|schedule_fired";
        if (observed < DISRUPTION_MINIMUM_OBSERVED) {
            state.observeChanged(holdKey, 0);
            return;
        }

        double fraction = (double) adrift / observed;
        boolean alreadyFired = state.lastReading(firedKey, 0) == 1;
        if (alreadyFired) {
            // Recovery: only once the village is comfortably back on its feet does this re-arm.
            if (fraction < DISRUPTION_FRACTION - DISRUPTION_RECOVERY_GAP) {
                state.observeChanged(firedKey, 0);
                state.observeChanged(holdKey, 0);
            }
            return;
        }
        if (fraction < DISRUPTION_FRACTION) {
            state.observeChanged(holdKey, 0);
            return;
        }

        int held = state.lastReading(holdKey, 0) + 1;
        state.observeChanged(holdKey, held);
        // Consecutive observations x the sweep interval is how much game time the disruption has
        // actually persisted for, so a server configured to scan less often still holds for as long.
        long scanInterval = Math.max(1, McaQuestsConfig.COMMON.situationDetectionIntervalTicks.get());
        if ((long) held * scanInterval < DISRUPTION_HOLD_TICKS) {
            return;
        }
        state.observeChanged(firedKey, 1);
        state.observeChanged(holdKey, 0);
        SituationManager.onSignal(server,
                TriggerSignal.townsteadScheduleDisruption(level, villageId, (float) fraction, observed));
        TownsteadCounters.signalFired();
    }

    // ----------------------------------------------------------------------------------- budgets

    /**
     * At most {@code maxVillagersPerPass} residents, starting at a rotating offset so a village larger
     * than the cap is covered across successive passes instead of the same prefix every time.
     */
    private static List<Entity> window(List<Entity> all, long rotation) {
        int cap = McaQuestsConfig.COMMON.townsteadMaxVillagersPerPass.get();
        if (all.size() <= cap) {
            return all;
        }
        List<Entity> window = new ArrayList<>(cap);
        int start = (int) Math.floorMod(rotation, all.size());
        for (int i = 0; i < cap; i++) {
            window.add(all.get((start + i) % all.size()));
        }
        return window;
    }
}
