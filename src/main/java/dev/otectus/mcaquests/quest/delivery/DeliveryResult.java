package dev.otectus.mcaquests.quest.delivery;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Every answer the delivery service can give, and the sentence the player is shown for it.
 *
 * <p>The whole point of naming these is that a refused hand-in used to say nothing at all: the old
 * villager-delivery path returned silently for the wrong recipient, for too few items and for a failed
 * transfer alike, so a player holding exactly what was asked for had no way to tell which of those
 * three had happened. A result is therefore never {@code null} and never a bare boolean.
 *
 * <p>Each value's key is derived from its name — {@code mcaquests.delivery.result.<snake_case>} — so a
 * new outcome cannot be added without a line for it, and the two counting results are the only ones
 * that take arguments (delivered, required).
 */
public enum DeliveryResult {
    /** Some units were committed; the obligation still wants more. Takes (delivered, required). */
    DELIVERED_PARTIAL(true, true),
    /** The last outstanding unit was committed. Takes (delivered, required). */
    DELIVERY_SATISFIED(true, true),
    /** A non-consuming proof objective was shown the full quantity; nothing was taken. */
    PROOF_ACKNOWLEDGED(true, false),
    /** Nothing outstanding: already paid in full, and never charged again. */
    ALREADY_DELIVERED(false, false),
    /** This villager is not the authorized recipient; the goods stay with the player. */
    WRONG_RECIPIENT(false, false),
    /** Nothing in the authorized source slots matches what the objective asked for. */
    NO_MATCHING_ITEMS(false, false),
    /** A transfer destination cannot take the batch, so none of it moves. */
    DESTINATION_FULL(false, false),
    /** The recipient is dead, unloaded, in another dimension, or out of reach. */
    RECIPIENT_UNAVAILABLE(false, false),
    /** The objective is suspended (optional-mod content missing), so it cannot take a deposit. */
    OBJECTIVE_PAUSED(false, false),
    /** Several obligations match and none of them is unambiguously the intended one. */
    AMBIGUOUS_DELIVERY(false, false),
    /** The world moved under the request: inventory changed, definition reshaped, quest gone. */
    STALE_REQUEST(false, false),
    /** Malformed or unauthorized request; the server did not mutate anything. */
    INVALID_REQUEST(false, false),
    /** The MCA Gift bridge is not available on this artifact, so use the quest menu instead. */
    BRIDGE_UNAVAILABLE(false, false);

    private final boolean success;
    private final boolean consumedUnits;
    private final String translationKey;

    DeliveryResult(boolean success, boolean consumedUnits) {
        this.success = success;
        this.consumedUnits = consumedUnits;
        this.translationKey = "mcaquests.delivery.result." + name().toLowerCase(Locale.ROOT);
    }

    /** True when the hand-in did what the player asked, whether or not it finished the objective. */
    public boolean isSuccess() {
        return success;
    }

    /**
     * True when this result means physical units left the player and were credited.
     *
     * <p>The MCA Gift bridge reads exactly this to decide whether to suppress the ordinary gift: a
     * quest that took the item owns the gesture, and a quest that did not must leave MCA's own
     * behaviour alone.
     */
    public boolean consumedUnits() {
        return consumedUnits;
    }

    public String translationKey() {
        return translationKey;
    }

    public Component message(Object... args) {
        return Component.translatable(translationKey, args);
    }
}
