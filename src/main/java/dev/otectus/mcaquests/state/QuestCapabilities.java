package dev.otectus.mcaquests.state;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * The accessor gameplay code uses to reach a player's {@link PlayerQuestData} (spec section 15).
 *
 * <p>PORT: the Forge capability behind this name is gone; the state now lives in the
 * {@link QuestAttachments#PLAYER_QUESTS} data attachment. The class and its {@code get} shape are kept
 * so the ~60 call sites across quests, projects, situations, commands and compat read unchanged. See
 * {@link QuestAttachments#get(Player)} for why the {@link Optional} is now always present.
 */
public final class QuestCapabilities {

    private QuestCapabilities() {
    }

    /** This player's quest state, created on first read. Never empty. */
    public static Optional<PlayerQuestData> get(Player player) {
        return QuestAttachments.get(player);
    }
}
