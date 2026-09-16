package dev.otectus.mcaquests.quest.delivery;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything a delivery line has to say, as separate facts.
 *
 * <p><b>Delivered</b>, <b>available</b> and <b>ready to claim</b> are three different things, and the
 * old menu conflated the first two: a player carrying two crossbows was shown a progress figure that
 * read as "handed over", while the server still considered nothing delivered. Keeping them apart in one
 * value is what lets the screen say "Delivered: 1 / 2   Available: 0" and mean it.
 *
 * <p>Plain data, computed on the server, with no networking of its own yet — the packet and the card
 * fields land in the next slice, and this is deliberately the shape they will be built from rather than
 * a second source of truth beside them.
 *
 * @param instance        the active quest copy, for a request that must not be applied to another copy
 *                        of the same quest
 * @param delivered       units already committed, from the ledger
 * @param required        units the obligation asks for
 * @param available       matching units in the slots the delivery is actually allowed to spend
 * @param recipientUuid   the authorized recipient when they are loaded, else {@code null}
 * @param deliverableHere whether the villager this view was built for may take the goods right now
 * @param giftCapable     whether MCA's Gift gesture can also pay this obligation
 * @param proofOnly       a {@code consume: false} objective that is shown goods rather than given them
 * @param reason          why the action is unavailable, when it is
 */
public record DeliveryView(ResourceLocation questId, @Nullable UUID instance, int objectiveIndex,
                           Component itemName, int delivered, int required, int available,
                           Component recipientName, @Nullable UUID recipientUuid,
                           boolean deliverableHere, boolean giftCapable, boolean proofOnly,
                           @Nullable DeliveryResult reason) {

    /** Units still owed. Never negative, even if a datapack shrank the requirement after a deposit. */
    public int remaining() {
        return Math.max(0, required - delivered);
    }

    /** How many units a Deliver action would move right now: the lesser of stock and what is owed. */
    public int deliverableNow() {
        return Math.max(0, Math.min(available, remaining()));
    }

    /**
     * True when the obligation is answered and its control should be disabled rather than hidden.
     *
     * <p>A proof objective is answered either way it can be: a villager proof once it has been shown
     * (which is sticky, and arrives here as {@code delivered}), an inventory proof while the player is
     * carrying all of them (which is live, and arrives as {@code available}).
     */
    public boolean satisfied() {
        return proofOnly ? (delivered >= required || available >= required) : remaining() == 0;
    }

    public Optional<DeliveryResult> reasonIfAny() {
        return Optional.ofNullable(reason);
    }

    /** The same view with a refusal attached, for the paths that discover the blocker after building it. */
    public DeliveryView withReason(@Nullable DeliveryResult newReason) {
        return new DeliveryView(questId, instance, objectiveIndex, itemName, delivered, required, available,
                recipientName, recipientUuid, deliverableHere && newReason == null, giftCapable, proofOnly,
                newReason);
    }
}
