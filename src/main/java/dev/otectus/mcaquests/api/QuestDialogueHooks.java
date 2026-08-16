package dev.otectus.mcaquests.api;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Guarded holder for the optional {@link QuestDialogueResolver}. An add-on registers its resolver during
 * its own setup with {@link #setResolver}; MCA: Quests calls {@link #resolve} at every lifecycle-dialogue
 * build point. With no resolver registered (the default) {@link #resolve} returns the static fallback, so
 * quest text is unchanged when no add-on is present — the degrade-to-static guarantee. A resolver that
 * throws or returns {@code null} also falls back, so a misbehaving add-on can never break the quest UI.
 */
public final class QuestDialogueHooks {

    @Nullable
    private static volatile QuestDialogueResolver resolver;

    private QuestDialogueHooks() {
    }

    /** Registers (or clears, with {@code null}) the add-on dialogue resolver. Last writer wins. */
    public static void setResolver(@Nullable QuestDialogueResolver newResolver) {
        resolver = newResolver;
    }

    /**
     * Returns the resolver's voiced line for this state, or {@code fallback} when no resolver is set, it
     * returns {@code null}, or it throws.
     */
    public static Component resolve(ServerPlayer player, @Nullable Entity villager, QuestDefinition def,
                                    String lifecycleState, Component fallback) {
        QuestDialogueResolver active = resolver;
        if (active == null) {
            return fallback;
        }
        try {
            Component voiced = active.resolve(player, villager, def, lifecycleState, fallback);
            return voiced != null ? voiced : fallback;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Dialogue resolver threw for state '{}'; using static fallback.",
                    lifecycleState, t);
            return fallback;
        }
    }
}
