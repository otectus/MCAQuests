package dev.otectus.mcaquests.api;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Marker for a {@link dev.otectus.mcaquests.quest.objective.QuestObjective} whose progress is driven by an
 * <b>external signal</b> pushed in by another mod, rather than by one of MCA: Quests' own built-in event or
 * poll detectors. An add-on advances all matching objectives by calling
 * {@link dev.otectus.mcaquests.quest.QuestManager#notifyExternalObjective(net.minecraft.server.level.ServerPlayer,
 * ResourceLocation, UUID)} — for example MCA: Conversations signalling "the player talked to this villager about
 * topic Y" when a conversation records that topic.
 *
 * <p>Generic on purpose: the objective decides what a signal id means and whether the (optional) villager
 * must match. Implementations should also be {@code isEventDriven() == true} so progress accumulates via the
 * signal instead of a live state check.
 */
public interface ExternalSignalObjective {

    /**
     * @param signalId     the pushed signal (e.g. a MCA: Conversations topic id).
     * @param villagerUuid the villager the signal is about, or {@code null} if not villager-specific.
     * @return true if this objective should advance for the given signal.
     */
    boolean matchesSignal(ResourceLocation signalId, @Nullable UUID villagerUuid);
}
