package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One objective as the client shows it: the line to read, the numbers behind it, what state it is in,
 * and something to draw beside it.
 *
 * <p>The counts used to be baked into the text — {@code objectiveLines} appended a literal
 * {@code "  (3/24)"} — which meant the client had a sentence and no data. It could not draw a
 * progress bar, could not colour a finished objective differently from an unstarted one, and could
 * not put a tick beside one that was done. Sending the numbers as numbers is what makes all three
 * possible; the text is now just the text.
 *
 * <p>The four delivery fields are the second thing the client could not previously be told. An item
 * hand-in has <b>three separate facts</b> — what has been handed over, what the player is carrying,
 * and whether this villager may take it — and the old shape had one number for all of it, so a card
 * showed a player carrying two crossbows as though they had already delivered them. They are appended
 * here rather than parsed back out of the sentence, and they are what the Deliver action is drawn and
 * enabled from.
 *
 * @param state     what has become of this objective. See {@link State}.
 * @param current   progress toward {@code required}; {@code 0} on an offer, which nobody has started
 * @param required  the target, or {@code 0} for an objective that is not counted
 * @param icon      an item to draw beside the line, or {@link ItemStack#EMPTY}
 * @param delivered units actually handed over and recorded, which is never inferred from possession
 * @param available matching units in the slots a hand-in is allowed to spend — the same slot policy
 *                  the transaction uses, so the screen cannot promise stock the server will refuse
 * @param delivery  what the player may do about this obligation here. See {@link Delivery}.
 * @param giftCapable whether MCA's Gift gesture can also pay it on this installation, which is a
 *                  different question from whether MCA is installed
 * @param reason    why the action is unavailable, ready to show as a tooltip; empty when it is not
 */
public record CardObjective(Component text, int current, int required, State state, ItemStack icon,
                            int delivered, int available, Delivery delivery, boolean giftCapable,
                            Component reason) {

    /**
     * Clamped rather than trusted, so a malformed line cannot make the screen draw a negative bar or
     * throw while rendering a tooltip that is not there.
     */
    public CardObjective {
        current = Math.max(0, current);
        required = Math.max(0, required);
        delivered = Math.max(0, delivered);
        available = Math.max(0, available);
        state = state == null ? State.PENDING : state;
        delivery = delivery == null ? Delivery.NONE : delivery;
        icon = icon == null ? ItemStack.EMPTY : icon;
        reason = reason == null ? Component.empty() : reason;
    }

    /**
     * The four things that can be true of an objective, as something other than a colour.
     *
     * <p>This replaced a single {@code boolean unavailable}, which could say "on hold" but had no way
     * to say "the person this was about is dead" — and the screens had no way to draw the difference
     * either, so a quest whose target had died looked exactly like a quest waiting on an uninstalled
     * mod. Both are drawn with their own glyph now, which is also what keeps the four states apart
     * for a player who cannot rely on colour.
     *
     * <p>The ordinal is on the wire, so entries are appended, never reordered.
     */
    public enum State {
        /** Not done, and nothing is wrong. */
        PENDING,
        /** Done. */
        DONE,
        /**
         * Cannot be evaluated right now — the canonical case is an optional companion mod that was
         * installed when the quest was accepted and has since been removed. Not failure and not
         * completion, so no counter is drawn: a "0/45" beside something nothing can advance reads as
         * failure, and the number would be a frozen baseline anyway.
         */
        UNAVAILABLE,
        /**
         * The villager this objective was about has died or can no longer be found anywhere. The quest
         * may not have failed yet — {@code fail_on_target_lost} decides that — but the objective is
         * not going to advance, and saying so is more honest than a paused counter.
         */
        LOST;

        private static final State[] VALUES = values();

        /** Decodes an ordinal off the wire, defaulting to {@link #PENDING} rather than throwing. */
        public static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PENDING;
        }
    }

    /**
     * What a player may do about an item obligation at the villager this card was built for.
     *
     * <p>Kept apart from {@link State}, which says what has become of an objective. A delivery can be
     * pending and payable here, pending and payable only somewhere else, or not a delivery at all, and
     * those are three different buttons.
     *
     * <p>The ordinal is on the wire, so entries are appended, never reordered.
     */
    public enum Delivery {
        /** Not an item obligation, or nothing about it to act on. */
        NONE,
        /** An item hand-in this villager can take right now. */
        DELIVER_HERE,
        /** An item hand-in that cannot be paid here or now; {@link #reason()} says why. */
        DELIVER_BLOCKED,
        /** A "show me the goods" obligation this villager can acknowledge now; nothing is taken. */
        SHOW_HERE,
        /** A "show me the goods" obligation that cannot be acknowledged here; see {@link #reason()}. */
        SHOW_BLOCKED,
        /**
         * An item obligation with nothing left to do.
         *
         * <p>Its own value rather than {@link #NONE} because the numbers still matter: a finished
         * delivery reads "Delivered: 2 / 2", which is exactly the line a player wants after handing
         * two crossbows over one at a time. {@code NONE} means "not an item obligation at all".
         */
        SETTLED,

        /**
         * A "show me the goods" obligation that has been answered. Nothing was ever taken.
         *
         * <p>Kept apart from {@link #SETTLED} because the difference is the whole of what abandoning
         * costs. A settled delivery means goods the player no longer has and will not get back; a
         * shown one means goods still in their pack. Collapsing the two is what would make the abandon
         * warning claim a loss that never happened — and a warning that is sometimes false stops being
         * read.
         */
        SHOWN;

        private static final Delivery[] VALUES = values();

        /** Decodes an ordinal off the wire, defaulting to {@link #NONE} rather than throwing. */
        public static Delivery byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }

        /** True for every item obligation, including a finished one that still has numbers to show. */
        public boolean isDelivery() {
            return this != NONE;
        }

        /** True when the action is enabled rather than explained. */
        public boolean actionable() {
            return this == DELIVER_HERE || this == SHOW_HERE;
        }

        /** True when there is an action to draw at all, enabled or explained. */
        public boolean hasAction() {
            return this != NONE && this != SETTLED && this != SHOWN;
        }

        /** True when the obligation is shown rather than handed over. */
        public boolean proof() {
            return this == SHOW_HERE || this == SHOW_BLOCKED || this == SHOWN;
        }

        /** True when the obligation is answered and nothing more will happen to it here. */
        public boolean answered() {
            return this == SETTLED || this == SHOWN;
        }
    }

    /**
     * The pre-1.6.5 shape, for every objective that is not an item hand-in.
     *
     * <p>Retained rather than migrated: some thirty call sites build ordinary objectives, and an
     * add-on may too. A delivery objective is built with the full constructor.
     */
    public CardObjective(Component text, int current, int required, State state, ItemStack icon) {
        this(text, current, required, state, icon, 0, 0, Delivery.NONE, false, Component.empty());
    }

    /** An objective on an offer: nothing started, nothing wrong. */
    public static CardObjective offered(Component text, int required, ItemStack icon) {
        return new CardObjective(text, 0, required, State.PENDING, icon);
    }

    /** Units still owed on a delivery. Never negative, even if a datapack shrank the requirement. */
    public int remaining() {
        return Math.max(0, required - delivered);
    }

    /** How many units a hand-in would move right now: the lesser of stock and what is owed. */
    public int deliverableNow() {
        return Math.max(0, Math.min(available, remaining()));
    }

    /** Whether there is a sentence to show for a disabled action. */
    public boolean hasReason() {
        return !reason.getString().isEmpty();
    }

    /** Whether this objective is done. */
    public boolean satisfied() {
        return state == State.DONE;
    }

    /**
     * Whether this objective's counter is meaningless right now.
     *
     * <p>Kept as a question the screens can ask, because both non-progressing states answer it the
     * same way even though they are drawn differently.
     */
    public boolean unavailable() {
        return state == State.UNAVAILABLE || state == State.LOST;
    }

    public static void encode(FriendlyByteBuf buf, CardObjective objective) {
        buf.writeComponent(objective.text);
        buf.writeVarInt(objective.current);
        buf.writeVarInt(objective.required);
        buf.writeVarInt(objective.state.ordinal());
        buf.writeItem(objective.icon);
        buf.writeVarInt(objective.delivered);
        buf.writeVarInt(objective.available);
        buf.writeVarInt(objective.delivery.ordinal());
        buf.writeBoolean(objective.giftCapable);
        buf.writeComponent(objective.reason);
    }

    public static CardObjective decode(FriendlyByteBuf buf) {
        return new CardObjective(
                buf.readComponent(),
                buf.readVarInt(),
                buf.readVarInt(),
                State.byOrdinal(buf.readVarInt()),
                buf.readItem(),
                buf.readVarInt(),
                buf.readVarInt(),
                Delivery.byOrdinal(buf.readVarInt()),
                buf.readBoolean(),
                buf.readComponent());
    }
}
