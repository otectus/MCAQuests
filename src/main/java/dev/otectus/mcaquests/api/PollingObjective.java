package dev.otectus.mcaquests.api;

import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link QuestObjective} advanced by the ~1/sec active-quest poll (see the player-tick handler in
 * {@code event.QuestProgressEvents}) rather than by a Forge game event. Any objective type that
 * implements this interface is automatically picked up by the generic poll pass added there — no
 * per-type wiring is needed for new implementations (spec section 11.3).
 *
 * <p><b>Contract:</b>
 * <ul>
 *     <li>{@link #poll} must be <b>cheap</b> — no world/chunk scans — since it runs for every player
 *     with a matching active quest, roughly once per second.</li>
 *     <li>{@link #poll} must be <b>idempotent</b>: calling it repeatedly against unchanged
 *     player/world state must not re-advance progress (mirror how e.g. {@code VisitBiomeObjective}
 *     guards on {@code progress.count() == 0} before setting it).</li>
 *     <li>{@link #poll} must return {@code true} <b>only</b> when this call actually changed
 *     {@code progress} — the caller's ready-check + log-sync sequencing relies on that signal.</li>
 * </ul>
 *
 * <p><b>Do not also register the implementing type in the hardcoded poll list</b> inside
 * {@code QuestProgressEvents}'s player-tick handler: that list and the generic
 * {@code PollingObjective} pass both run on the same ~1/sec cadence, so a type present in both would
 * have its progress advanced twice per tick.
 */
public interface PollingObjective extends QuestObjective {

    /**
     * Advances this objective's progress if the player currently satisfies whatever it is watching
     * for. Called roughly once per second for every active quest whose resolved objective implements
     * this interface.
     *
     * @return true if progress changed (triggers the same ready-check + log sync as the built-in
     *         polled objectives).
     */
    boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress);
}
