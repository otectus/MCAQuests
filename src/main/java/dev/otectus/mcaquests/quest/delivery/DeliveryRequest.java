package dev.otectus.mcaquests.quest.delivery;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One intention to hand goods over: <em>this</em> obligation, to <em>that</em> villager, from
 * <em>these</em> slots, up to <em>this</em> many units.
 *
 * <p>Deliberately identifiers and counts only. Nothing here is progress, nothing here is a resolved
 * entity, and nothing here is trusted: the request says what the player asked for, and
 * {@link DeliveryService} decides what is true. That is what lets the same value carry a click in the
 * quest menu, an MCA Gift gesture, and (in the next slice) a client packet without a second validation
 * path growing beside the first.
 *
 * @param instance       the active quest copy this is about, or {@code null} to match by quest id alone
 *                       (a pre-1.6.5 quest has not minted one yet)
 * @param objectiveIndex index into the <b>resolved</b> definition's objective list
 * @param requestedUnits the ceiling the player authorized; {@link #ALL_UNITS} for "as much as is
 *                       outstanding", which is what a plain Deliver click means
 * @param slots          the source slots the player authorized, or empty for the default policy
 *                       ({@link dev.otectus.mcaquests.quest.objective.InventoryTransfer#defaultSourceSlots})
 * @param expectedRevision the obligation's committed-unit count as the client last saw it, or
 *                       {@link #NO_REVISION} for a route with nothing to be stale about. A delivery
 *                       obligation's revision <em>is</em> its delivered count: it changes on exactly
 *                       the event a replayed click must not be applied twice across, so a mismatch is
 *                       a stale card rather than a second hand-in
 * @param requestId      the client's identity for this one click, or {@code null} for a route the
 *                       server itself drove. Present ids are claimed once, so a duplicated packet is
 *                       refused rather than charged; it also tells {@link DeliveryService} that the
 *                       caller owns the screen refresh
 */
public record DeliveryRequest(@Nullable UUID instance, ResourceLocation questId, int objectiveIndex,
                              UUID recipientUuid, Method method, int requestedUnits, IntSet slots,
                              int expectedRevision, @Nullable UUID requestId) {

    /** How the player expressed the intention, which decides the slot policy and the feedback channel. */
    public enum Method {
        /** The explicit Deliver action on a quest card. Bulk, and the only one that can select slots. */
        MENU,
        /** MCA's own Gift gesture: one unit, from the actual main hand, per invocation. */
        GIFT,
        /** The pre-1.6.5 right-click hand-off, kept behind {@code legacyInteractDelivery}. */
        LEGACY_INTERACT,
        /** Final turn-in, which pays whatever is still outstanding as part of completion. */
        TURN_IN
    }

    /** "Everything still owed", so the common case does not have to restate the requirement. */
    public static final int ALL_UNITS = -1;

    /**
     * A hard ceiling on a single request, comfortably above a full player inventory of any stackable
     * item. A request is a number from a client, so it is bounded before it reaches a loop.
     */
    public static final int MAX_UNITS = 2304;

    /** "Do not check the revision", for the entry points that are not replaying a rendered card. */
    public static final int NO_REVISION = -1;

    /** The most slots one request may name: a whole player inventory, offhand included. */
    public static final int MAX_SLOTS = 41;

    public DeliveryRequest {
        objectiveIndex = Math.max(0, objectiveIndex);
        requestedUnits = requestedUnits == ALL_UNITS ? ALL_UNITS : Math.max(0, Math.min(MAX_UNITS, requestedUnits));
        slots = slots == null ? IntSets.EMPTY_SET : slots;
        expectedRevision = expectedRevision < 0 ? NO_REVISION : expectedRevision;
    }

    /** A plain Deliver click: everything outstanding, from the default slot policy. */
    public static DeliveryRequest menu(@Nullable UUID instance, ResourceLocation questId, int objectiveIndex,
                                       UUID recipientUuid) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.MENU,
                ALL_UNITS, IntSets.EMPTY_SET, NO_REVISION, null);
    }

    /** A Deliver click with an explicit quantity and stack selection. */
    public static DeliveryRequest menu(@Nullable UUID instance, ResourceLocation questId, int objectiveIndex,
                                       UUID recipientUuid, int units, IntSet slots) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.MENU, units, slots,
                NO_REVISION, null);
    }

    /**
     * A Deliver click that arrived over the wire: everything above, plus the two things only a
     * networked request has — the card revision it was made against, and its own identity.
     */
    public static DeliveryRequest fromMenuPacket(@Nullable UUID instance, ResourceLocation questId,
                                                 int objectiveIndex, UUID recipientUuid, int units,
                                                 IntSet slots, int expectedRevision, UUID requestId) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.MENU, units, slots,
                expectedRevision, requestId);
    }

    /**
     * One unit from one named slot, for MCA's Gift gesture.
     *
     * <p>The slot is part of the authorization, not a hint. A player deliberately holding their named
     * crossbow is authorizing that crossbow; nothing may go looking for an equivalent stack elsewhere.
     */
    public static DeliveryRequest gift(@Nullable UUID instance, ResourceLocation questId, int objectiveIndex,
                                       UUID recipientUuid, int heldSlot) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.GIFT, 1,
                IntSets.singleton(heldSlot), NO_REVISION, null);
    }

    /**
     * One obligation's share of a final turn-in: everything still owed, from the default slot policy.
     *
     * <p>Turn-in pays several obligations inside one transaction, so this names a single one of them;
     * {@link DeliveryService#planTurnIn} builds one per outstanding delivery and reserves them all
     * against the same plan. Going through a request rather than reserving directly is what keeps the
     * slot policy and the outstanding-units clamp identical to the Deliver button's — the bug this
     * exists to prevent is a turn-in quietly taking the armour the player is wearing.
     */
    public static DeliveryRequest turnIn(@Nullable UUID instance, ResourceLocation questId, int objectiveIndex,
                                         UUID recipientUuid) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.TURN_IN,
                ALL_UNITS, IntSets.EMPTY_SET, NO_REVISION, null);
    }

    /** The legacy right-click hand-off: the whole payload at once, from the default slot policy. */
    public static DeliveryRequest legacyInteract(@Nullable UUID instance, ResourceLocation questId,
                                                 int objectiveIndex, UUID recipientUuid) {
        return new DeliveryRequest(instance, questId, objectiveIndex, recipientUuid, Method.LEGACY_INTERACT,
                ALL_UNITS, IntSets.EMPTY_SET, NO_REVISION, null);
    }

    /** True when this request carries a revision the server must check before committing. */
    public boolean hasRevision() {
        return expectedRevision != NO_REVISION;
    }

    /** True when the player named the stacks to spend rather than leaving it to the default policy. */
    public boolean hasExplicitSlots() {
        return !slots.isEmpty();
    }

    /** The ceiling this request puts on a deposit, given what is still owed. */
    public int authorizedUnits(int outstanding) {
        return requestedUnits == ALL_UNITS ? outstanding : Math.min(requestedUnits, outstanding);
    }
}
