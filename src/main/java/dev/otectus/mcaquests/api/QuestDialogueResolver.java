package dev.otectus.mcaquests.api;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Optional hook an add-on can register (via {@link QuestDialogueHooks#setResolver}) to render a quest's
 * lifecycle dialogue line in its own voice — e.g. MCA: Conversations speaking the offer / in-progress /
 * ready / complete / failed line in the villager's personality — instead of the quest's static
 * {@code dialogue} text.
 *
 * <p>Called <b>server-side</b> at Component-build time (the quest card is built and shipped whole). The
 * resolver receives everything it needs to decide: the player, the giver/turn-in villager (nullable, e.g.
 * self-complete), the quest {@link QuestDefinition} (its id, category, and raw {@code dialogue} map), the
 * lifecycle state (one of {@link QuestDefinition#OFFER} … {@link QuestDefinition#FAILED}), and the
 * already-resolved {@code fallback} Component. Return {@code null} to defer to that fallback.
 */
@FunctionalInterface
public interface QuestDialogueResolver {

    /**
     * @return a voiced line for this state, or {@code null} to use {@code fallback} (the quest's own text).
     */
    @Nullable
    Component resolve(ServerPlayer player, @Nullable Entity villager, QuestDefinition def,
                      String lifecycleState, Component fallback);
}
