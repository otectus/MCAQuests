package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.compat.TownsteadPeriod;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalKeys;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Turns a Townstead <em>event</em> into the same situation signal the polling detector would have
 * produced for the same moment -- once, whichever of the two noticed it first.
 *
 * <p>Both paths read and write {@link TownsteadSignalStateSavedData} under the keys in
 * {@link TownsteadSignalKeys}. The scan observes <em>state</em>, so for it a first sighting is never
 * news: there is nothing to compare against, and announcing it would open a situation for every
 * villager on an existing world. An event is different: it is a <em>transition</em>, and it says
 * what it changed from. So when an event arrives for a key that has no baseline yet, the event's
 * own before/after decides, the after value is recorded, and the next scan finds the baseline
 * already moved. When a baseline exists, the event defers to it exactly as the scan does, which is
 * what makes event-then-scan, scan-then-event, and the same event delivered twice all produce one
 * signal.
 *
 * <p>This class names nothing of Townstead's. The typed adapter under {@code compat.townstead.v1}
 * unpacks the API records into the primitives here, so the dedup logic is testable without the
 * API and stays identical however the values arrived.
 */
public final class TownsteadEventSignals {

    /** A reading the store has never seen; real readings are tiers, hashes and flags, never this. */
    private static final int UNSEEN = Integer.MIN_VALUE;

    private final TownsteadSignalStateSavedData state;
    private final Predicate<SituationSignalType> wants;
    private final Consumer<TriggerSignal> sink;

    /**
     * @param state the shared baseline store
     * @param wants whether any loaded, enabled definition consumes a signal type -- nothing is
     *              observed for a type nobody wants, which is also what the scan does
     * @param sink  where signals go; production hands them to {@code SituationManager.onSignal}
     */
    public TownsteadEventSignals(TownsteadSignalStateSavedData state, Predicate<SituationSignalType> wants,
                                 Consumer<TriggerSignal> sink) {
        this.state = state;
        this.wants = wants;
        this.sink = sink;
    }

    // --- villagers -------------------------------------------------------------------------------

    /** A villager collapsed. Fires unless the scan already saw them down. */
    public void collapsed(@Nullable ServerLevel level, int villageId, UUID uuid) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_COLLAPSE)) {
            return;
        }
        String key = TownsteadSignalKeys.villager(uuid) + "|collapsed";
        boolean known = state.lastReading(key, UNSEEN) != UNSEEN;
        boolean rose = state.observeRisingEdge(key, true);
        if (rose || !known) {
            fire(TriggerSignal.townsteadCollapse(level, villageId, uuid));
        }
    }

    /** A villager got back up: lower the edge so the next collapse is news again. Never a signal. */
    public void recovered(UUID uuid) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_COLLAPSE)) {
            return;
        }
        state.observeRisingEdge(TownsteadSignalKeys.villager(uuid) + "|collapsed", false);
    }

    /** A worker reached a new tier. The baseline, when there is one, says what tier they rose from. */
    public void tierChanged(@Nullable ServerLevel level, int villageId, UUID uuid, String professionId,
                            int tierBefore, int tierAfter) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_PROFESSION_TIER) || professionId == null
                || professionId.isEmpty()) {
            return;
        }
        String key = TownsteadSignalKeys.villager(uuid) + "|tier|" + professionId;
        boolean known = state.lastReading(key, UNSEEN) != UNSEEN;
        int previous = known ? state.lastReading(key, tierBefore) : tierBefore;
        boolean rose = state.observeIncrease(key, tierAfter);
        if (known ? rose : tierAfter > tierBefore) {
            fire(TriggerSignal.townsteadProfessionTier(level, villageId, uuid, professionId, previous, tierAfter));
        }
    }

    /**
     * A villager crossed into another life stage. Three axes, as the scan observes them: the raw
     * stage id, the senior flag, and the stage's canonical presentation from its root definition.
     * The event knows the previous raw stage; the caller resolves both canonical stages.
     */
    public void lifeStageChanged(@Nullable ServerLevel level, int villageId, UUID uuid, String stageBefore,
                                 String stageAfter, boolean senior, String canonicalBefore, String canonicalAfter) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_LIFE_TRANSITION)) {
            return;
        }
        String prefix = TownsteadSignalKeys.villager(uuid);
        labelTransition(prefix + "|senior", null, String.valueOf(senior))
                .ifPresent(previous -> fire(TriggerSignal.townsteadLifeTransition(level, villageId, uuid,
                        "senior", previous, String.valueOf(senior))));
        labelTransition(prefix + "|life_stage", stageBefore, stageAfter)
                .ifPresent(previous -> fire(TriggerSignal.townsteadLifeTransition(level, villageId, uuid,
                        "life_stage", previous, stageAfter)));
        labelTransition(prefix + "|canonical_stage", canonicalBefore, canonicalAfter)
                .ifPresent(previous -> fire(TriggerSignal.townsteadLifeTransition(level, villageId, uuid,
                        "canonical_stage", previous, canonicalAfter)));
    }

    /** A villager died: their baselines will never be compared against again. */
    public void died(UUID uuid) {
        String prefix = TownsteadSignalKeys.villager(uuid);
        state.forget(prefix + "|collapsed");
        state.forget(prefix + "|senior");
        state.forget(prefix + "|life_stage");
        state.forget(prefix + "|canonical_stage");
    }

    // --- villages --------------------------------------------------------------------------------

    /** A village's spirit readout changed shape. Tier rises, identity changes and classification changes each count. */
    public void spiritChanged(@Nullable ServerLevel level, int villageId, int tierBefore, int tierAfter,
                              String primaryBefore, String primaryAfter, String classBefore, String classAfter) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_SPIRIT)) {
            return;
        }
        String village = TownsteadSignalKeys.village(level, villageId);
        String tierKey = village + "|spirit";
        boolean tierKnown = state.lastReading(tierKey, UNSEEN) != UNSEEN;
        int previousTier = tierKnown ? state.lastReading(tierKey, tierAfter) : tierBefore;
        boolean rose = state.observeIncrease(tierKey, tierAfter);
        boolean roseATier = tierKnown ? rose : tierAfter > tierBefore;

        String identityKey = village + "|spirit_id";
        String primary = primaryAfter == null ? "" : primaryAfter;
        boolean identityKnown = state.lastReading(identityKey, UNSEEN) != UNSEEN;
        boolean changed = state.observeChanged(identityKey, primary.hashCode());
        boolean changedIdentity = identityKnown ? changed : !primary.equals(primaryBefore == null ? "" : primaryBefore);

        String previousClassification = labelTransition(village + "|spirit_class", classBefore, classAfter).orElse(null);

        if (roseATier || changedIdentity || previousClassification != null) {
            fire(TriggerSignal.townsteadSpirit(level, villageId, primary, previousTier, tierAfter,
                    previousClassification, classAfter));
        }
    }

    /**
     * A building was established or upgraded. The scan keys one signature for the whole register;
     * the caller recomputes that signature from the same register so the two agree: an event for a
     * change the scan already reported is silent, and a scan after an event finds its baseline moved.
     *
     * @param signature the register signature as the scan computes it, or empty when the register
     *                  could not be read -- then nothing can be compared, the event is taken at its
     *                  word and the stale baseline is dropped so the next scan re-seeds quietly
     */
    public void building(@Nullable ServerLevel level, int villageId, OptionalInt signature, String family, int tier) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_BUILDING)) {
            return;
        }
        String key = TownsteadSignalKeys.village(level, villageId) + "|buildings";
        if (signature.isEmpty()) {
            state.forget(key);
            fire(TriggerSignal.townsteadBuilding(level, villageId, family, tier));
            return;
        }
        boolean known = state.lastReading(key, UNSEEN) != UNSEEN;
        boolean changed = state.observeChanged(key, signature.getAsInt());
        if (changed || !known) {
            fire(TriggerSignal.townsteadBuilding(level, villageId, family, tier));
        }
    }

    /**
     * The calendar rolled over a day. Every period that actually changed is announced once per
     * village handed in, under the scan's own per-village key; a period that did not change costs
     * nothing. A profile switch is never a transition -- two calendars are not comparable -- so the
     * new profile is only seeded.
     */
    public void dayRolledOver(TownsteadCalendarView before, TownsteadCalendarView after, List<VillagePlace> villages) {
        if (!wants.test(SituationSignalType.TOWNSTEAD_CALENDAR_TRANSITION) || after == null
                || after.profileId().isEmpty()) {
            return;
        }
        boolean comparable = before != null && after.profileId().equals(before.profileId());
        for (TownsteadPeriod period : TownsteadPeriod.values()) {
            String valueAfter = period.currentValue(after);
            if (valueAfter.isEmpty()) {
                continue;
            }
            String valueBefore = comparable ? period.currentValue(before) : "";
            if (valueBefore.equals(valueAfter)) {
                continue;
            }
            for (VillagePlace village : villages) {
                ResourceLocation dimension = village.level() == null ? null : village.level().dimension().location();
                String key = TownsteadSignalKeys.calendar(after.profileId(), period.id(), dimension, village.villageId());
                labelTransition(key, valueBefore, valueAfter).ifPresent(previous -> fire(
                        TriggerSignal.townsteadCalendarTransition(village.level(), village.villageId(), period.id(),
                                previous, valueAfter)));
            }
        }
    }

    // --- plumbing --------------------------------------------------------------------------------

    /**
     * Records a named reading and reports what it changed from. With a baseline this is exactly
     * {@link TownsteadSignalStateSavedData#observeLabel}; without one the event's own
     * {@code before} stands in, so an authoritative transition is not lost to a missing baseline
     * while an event that knows no before (or reports none) only seeds.
     */
    private Optional<String> labelTransition(String key, @Nullable String before, @Nullable String after) {
        if (after == null || after.isEmpty()) {
            return Optional.empty();
        }
        boolean known = !state.lastLabel(key).isEmpty();
        Optional<String> observed = state.observeLabel(key, after);
        if (known) {
            return observed;
        }
        return before == null || before.isEmpty() || before.equals(after) ? Optional.empty() : Optional.of(before);
    }

    private void fire(TriggerSignal signal) {
        sink.accept(signal);
        TownsteadCounters.signalFired();
    }
}
