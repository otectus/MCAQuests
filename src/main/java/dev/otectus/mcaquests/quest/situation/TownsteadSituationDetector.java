package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.compat.TownsteadVillageBuilding;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
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
        TownsteadSignalStateSavedData state = TownsteadSignalStateSavedData.get(server);
        TownsteadEvaluation evaluation = new TownsteadEvaluation();

        boolean wantsNeeds = wants(SituationSignalType.TOWNSTEAD_NEED);
        boolean wantsCollapse = wants(SituationSignalType.TOWNSTEAD_COLLAPSE);
        boolean wantsTiers = wants(SituationSignalType.TOWNSTEAD_PROFESSION_TIER);
        if ((wantsNeeds || wantsCollapse || wantsTiers) && bridge.has(TownsteadCapability.READ_VILLAGER)) {
            scanResidents(server, level, villageId, rotation, state, evaluation,
                    wantsNeeds, wantsCollapse, wantsTiers);
        }
        if (wants(SituationSignalType.TOWNSTEAD_SPIRIT) && bridge.has(TownsteadCapability.READ_SPIRIT)) {
            scanSpirit(server, level, villageId, state, evaluation);
        }
        if (wants(SituationSignalType.TOWNSTEAD_BUILDING) && bridge.has(TownsteadCapability.READ_BUILDING)) {
            scanBuildings(server, level, villageId, state, evaluation);
        }
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
                                      boolean wantsCollapse, boolean wantsTiers) {
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
            }
            if (wantsTiers && view.hasProfession()) {
                String key = resident.getUUID() + "|tier|" + view.professionId();
                int previous = state.lastReading(key, view.professionLevel());
                if (state.observeIncrease(key, view.professionLevel())) {
                    SituationManager.onSignal(server, TriggerSignal.townsteadProfessionTier(level, villageId,
                            resident.getUUID(), view.professionId(), previous, view.professionLevel()));
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

        // The gap is read as a percentage of this need's own range, because the ranges differ.
        double hysteresis = McaQuestsConfig.COMMON.townsteadNeedCrisisHysteresis.get() / 100.0D;
        double leaveAt = CRISIS_FRACTION - hysteresis;
        boolean inCrisis = wasInCrisis ? fraction > Math.max(0.0D, leaveAt) : fraction >= CRISIS_FRACTION;

        if (state.observeChanged(key, inCrisis ? 1 : 0) && inCrisis) {
            SituationManager.onSignal(server, TriggerSignal.townsteadNeed(level, villageId, need,
                    (float) fraction, worst / Math.max(1, needMax)));
        }
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

        if (roseATier || changedIdentity) {
            SituationManager.onSignal(server, TriggerSignal.townsteadSpirit(level, villageId,
                    view.primaryId(), previous, view.tier()));
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
        }
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
