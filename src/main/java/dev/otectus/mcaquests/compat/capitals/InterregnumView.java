package dev.otectus.mcaquests.compat.capitals;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A vacant throne, as MCA: Quests reads it.
 *
 * <p>Capitals opens an interregnum when a sovereign dies and closes it when the succession resolves,
 * so this is a window rather than a state — the poller samples it and the quest content reacts to the
 * edge, not to the record.
 *
 * @param capitalId          the capital whose throne is vacant
 * @param deceasedSovereign  the sovereign who died, or null when Capitals did not record one
 * @param wasPlayerSovereign whether the vacated throne was held by a player
 */
public record InterregnumView(UUID capitalId, @Nullable UUID deceasedSovereign,
                              boolean wasPlayerSovereign) {
}
