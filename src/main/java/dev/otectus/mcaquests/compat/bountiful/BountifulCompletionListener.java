package dev.otectus.mcaquests.compat.bountiful;

import net.minecraft.server.level.ServerPlayer;

/**
 * Told about every Bountiful bounty a player successfully cashes in.
 *
 * <p>Registered through {@link BountifulBridge#addCompletionListener}, which is a no-op on every
 * bridge but the hooked one — so a listener that is never called is the normal, correct outcome on an
 * installation without Bountiful, and no caller needs to ask first.
 */
@FunctionalInterface
public interface BountifulCompletionListener {

    /**
     * Called on the server thread, after Bountiful has already accepted the cash-in. Must not throw
     * and must not block: this runs inside another mod's method.
     *
     * <p>The player is passed alongside the completion rather than resolved from its
     * {@link BountyCompletion#playerId()}, because every listener needs one and looking a UUID up
     * against the player list can only ever give back the player who is standing at the board.
     */
    void onBountyCompleted(ServerPlayer player, BountyCompletion completion);
}
