package dev.otectus.mcaquests.compat.mca;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.event.ConversationCredit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * MCA opened its dialogue for a player: the server-side proof of a real conversation (1.7.0).
 *
 * <p>Why a hook at all. A talk objective was credited from Forge's {@code EntityInteract} event, which
 * the server fires for the client's INTERACT packet. MCA 7.6.x (and 7.7.36 on NeoForge) opens its
 * conversation screen from {@code interactAt} and answers {@code SUCCESS}, and a client that gets a
 * consuming answer from {@code interactAt} never sends the INTERACT packet at all. So the event never
 * fired for an ordinary conversation — only for sneak-clicks, which open MCA's trade screen — and talk
 * objectives counted nothing unless MCA: Conversations reported the conversation through the API. MCA
 * 7.7.0/7.7.1 moved the same code into {@code mobInteract}, so the symptom depended on the MCA build.
 *
 * <p>Both paths end in {@code EntityCommandHandler.interactAt}, which is where MCA sends the player its
 * dialogue screen. The four mixin variants observe its return on the server and report here. They never
 * cancel or change anything; MCA's behaviour is exactly what it is without this mod.
 *
 * <p>Only a consuming, main-hand result counts: that is MCA saying "I opened the dialogue". A held item
 * does not disqualify it — MCA itself decides which items open a conversation (it refuses its editor
 * book, needle, comb and potions, and its configurable blacklist) — so a player holding a sword who
 * talks to a villager is now credited, while trading, the editor and inventory are not conversations.
 */
public final class McaDialogueHookEvents {

    private McaDialogueHookEvents() {
    }

    /**
     * Called by the mixins at every return of {@code interactAt}. Never throws: this runs inside MCA's
     * method, and an exception here would read as MCA's crash.
     */
    public static void onDialogueOpened(@Nullable Object handler, @Nullable Player player,
                                        @Nullable InteractionHand hand, @Nullable InteractionResult result) {
        if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND
                || result == null || !result.consumesAction()) {
            return;
        }
        try {
            if (serverPlayer.getServer() == null || !serverPlayer.getServer().isSameThread()) {
                return;
            }
            McaDialogueHookProbe.observed();
            Entity villager = McaHandles.commandHandlerEntity(handler);
            if (villager == null || !McaCompat.isMcaVillager(villager)) {
                return;
            }
            ConversationCredit.credit(serverPlayer, villager, ConversationCredit.Source.MCA_DIALOGUE);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Conversation hook declined an MCA dialogue after an error", t);
        }
    }
}
