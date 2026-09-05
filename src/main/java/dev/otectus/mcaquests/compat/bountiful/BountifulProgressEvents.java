package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.event.QuestProgressEvents;
import dev.otectus.mcaquests.quest.objective.BountifulBountiesObjective;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns an observed Bountiful cash-in into quest progress.
 *
 * <p>The whole of the join between the integration and the quest engine, and deliberately this small.
 * Everything upstream of it is about whether a cash-in can be seen at all; everything downstream is
 * ordinary objective crediting, identical to a mob kill. Keeping the seam here means the hook never
 * touches a player's quest data and the quest engine never learns that Bountiful exists.
 *
 * <p>Registered once at start-up rather than per bridge: {@link BountifulCompat} owns the listener
 * list precisely so a re-probe cannot silently unregister it.
 */
public final class BountifulProgressEvents implements BountifulCompletionListener {

    private BountifulProgressEvents() {
    }

    /**
     * Subscribes to completions. Called from the mod constructor, before anything could cash a bounty
     * in, and safe on an installation without Bountiful — the Noop bridge accepts the listener and
     * simply never calls it.
     */
    public static void init() {
        BountifulCompat.bridge().addCompletionListener(new BountifulProgressEvents());
    }

    /**
     * Credits one bounty to every matching objective.
     *
     * <p>Crediting through {@link QuestProgressEvents#creditObjectives} rather than directly is what
     * makes a suspended quest stay suspended: the gate that skips an objective whose
     * {@code unavailableReason} is present lives in there, and a completion arriving while the rarity
     * reader is unbound must not advance a {@code min_rarity} objective that cannot currently be
     * judged.
     */
    @Override
    public void onBountyCompleted(ServerPlayer player, BountyCompletion completion) {
        QuestProgressEvents.creditObjectives(player, BountifulBountiesObjective.class,
                objective -> objective.matches(completion), 1);
    }
}
