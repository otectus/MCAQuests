package dev.otectus.mcaquests.compat.mca;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.delivery.DeliveryResult;
import dev.otectus.mcaquests.quest.delivery.DeliveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The one place MCA's Gift gesture becomes a quest delivery.
 *
 * <p>The gesture is MCA's, not ours: a player presses Gift in MCA's own interaction screen and MCA's
 * {@code VillagerCommandHandler} is asked to handle the command {@code "gift"}. This class is what the
 * four mixin variants call at the top of that method, and its answer decides whether MCA's own gift
 * branch runs at all.
 *
 * <p><b>Only {@code "gift"} is ever looked at.</b> Every other command — trade, inventory, sethome,
 * procreate — leaves here as {@link Outcome#PASS_THROUGH} before anything is resolved, because
 * cancelling a command this mod has no opinion about would break MCA in a way that reads as MCA's own
 * bug.
 *
 * <p><b>It validates for itself.</b> Arriving through MCA's command packet is not proof of anything
 * this mod cares about: the villager must be a live MCA villager in the same dimension and in reach,
 * the player must be the one MCA has in this conversation, and the shared
 * {@link DeliveryService} then re-checks the recipient, the objective and the stock. Upstream is
 * trusted for none of it.
 *
 * <p><b>Quest payment is not a social gift.</b> A unit that pays a delivery awards no MCA hearts, no
 * mood change and no saturation: the quest's own rewards are the payment. That is why a handled
 * gesture cancels MCA's branch rather than running alongside it — running both would pay twice for one
 * item.
 */
public final class McaGiftHookEvents {

    /** MCA's own name for the gesture, as it appears in the command string. */
    private static final String GIFT_COMMAND = "gift";

    private McaGiftHookEvents() {
    }

    /**
     * What the mixin should do with this invocation.
     *
     * <p>Only {@link #PASS_THROUGH} leaves MCA's implementation alone. The other three mean the
     * gesture belonged to a quest — including when nothing moved, because a blocked quest hand-in that
     * fell through into an ordinary gift would take the requested item and credit nothing.
     */
    public enum Outcome {
        /** No quest wants this: MCA's ordinary gift runs exactly as it would without this mod. */
        PASS_THROUGH,
        /** Units were committed to a delivery. MCA's gift branch must not also run. */
        DELIVERED,
        /** A "show me the goods" objective was satisfied. Nothing was taken, and nothing is gifted. */
        PROOF_ACKNOWLEDGED,
        /** A quest wanted it but could not take it. Nothing was taken; the player was told why. */
        HANDLED_WITHOUT_TRANSFER;

        /** True when MCA's own handling of this invocation must be suppressed. */
        public boolean handled() {
            return this != PASS_THROUGH;
        }
    }

    /** True for the one command this bridge has anything to do with. */
    public static boolean routes(@Nullable String command) {
        return GIFT_COMMAND.equals(command);
    }

    /**
     * Routes one Gift invocation, or declines to.
     *
     * <p>Never throws: this runs inside somebody else's method, and an exception escaping here would
     * surface as a crash in MCA. Any failure reads as {@link Outcome#PASS_THROUGH}, which is the state
     * the game is in without this mod at all.
     *
     * @param handler the MCA command handler instance, untyped — {@link McaHandles} reads the villager
     *                and the interacting player out of it by name
     */
    public static Outcome handle(@Nullable Object handler, @Nullable ServerPlayer player,
                                 @Nullable String command) {
        if (!routes(command)) {
            return Outcome.PASS_THROUGH;
        }
        try {
            return route(handler, player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Gift bridge declined an invocation after an error; MCA's "
                    + "own gift behaviour applies", t);
            return Outcome.PASS_THROUGH;
        }
    }

    private static Outcome route(@Nullable Object handler, @Nullable ServerPlayer player) {
        if (handler == null || player == null || player.getServer() == null
                || !player.getServer().isSameThread()) {
            return Outcome.PASS_THROUGH;
        }
        // Reaching here is proof the hook applied, which is a stronger fact than the mixin plugin's
        // after-the-fact byte check and is recorded as such: the interface may promise Gift from now on.
        McaGiftHookProbe.observed();
        LivingEntity recipient = recipientOf(handler, player);
        if (recipient == null) {
            return Outcome.PASS_THROUGH;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            // Nothing in hand is MCA's business: its own gift branch has a message for it.
            return Outcome.PASS_THROUGH;
        }
        return classify(DeliveryService.giftHeld(player, recipient), player);
    }

    /**
     * The villager this handler speaks for, when the gesture may legitimately be routed to them.
     *
     * <p>Four things are checked and none of them is implied by the command having arrived: that the
     * owner is a live MCA villager, that the player is close enough to be talking to them at all (which
     * also covers the same dimension), and that MCA has <em>this</em> player in the conversation. The
     * last one is why the interacting player is bound at all: without it, a command handler reachable
     * from another player's session could route a gift on somebody else's behalf.
     */
    @Nullable
    private static LivingEntity recipientOf(Object handler, ServerPlayer player) {
        Entity owner = McaHandles.commandHandlerEntity(handler);
        if (!(owner instanceof LivingEntity recipient) || !recipient.isAlive()
                || !McaCompat.canPlayerInteract(player, recipient)) {
            return null;
        }
        Player interacting = McaHandles.commandHandlerInteractingPlayer(handler);
        if (interacting != null && !interacting.getUUID().equals(player.getUUID())) {
            return null;
        }
        return recipient;
    }

    /**
     * Turns the delivery service's answer into the mixin's instruction, and tells the player.
     *
     * <p>Pure apart from the message, so the mapping can be asserted directly: an empty answer is the
     * only {@link Outcome#PASS_THROUGH}, a success that moved units is {@link Outcome#DELIVERED}, a
     * proof is its own case, and <em>every</em> other present answer is handled without a transfer.
     *
     * <p>The player is told here rather than by MCA, because MCA's branch is the thing being
     * suppressed. Its own screen stays open — nothing pushes a quest menu at a player mid-conversation.
     */
    static Outcome classify(Optional<DeliveryService.Outcome> answer, @Nullable ServerPlayer player) {
        Outcome outcome = classify(answer);
        if (outcome.handled() && player != null) {
            answer.ifPresent(result -> player.sendSystemMessage(result.message()));
        }
        return outcome;
    }

    /** The mapping itself, with nothing to tell and nobody to tell it to. */
    public static Outcome classify(Optional<DeliveryService.Outcome> answer) {
        if (answer.isEmpty()) {
            return Outcome.PASS_THROUGH;
        }
        DeliveryService.Outcome result = answer.get();
        if (result.result() == DeliveryResult.PROOF_ACKNOWLEDGED) {
            return Outcome.PROOF_ACKNOWLEDGED;
        }
        return result.consumedUnits() ? Outcome.DELIVERED : Outcome.HANDLED_WITHOUT_TRANSFER;
    }
}
