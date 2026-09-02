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
 * @param state    what has become of this objective. See {@link State}.
 * @param current  progress toward {@code required}; {@code 0} on an offer, which nobody has started
 * @param required the target, or {@code 0} for an objective that is not counted
 * @param icon     an item to draw beside the line, or {@link ItemStack#EMPTY}
 */
public record CardObjective(Component text, int current, int required, State state, ItemStack icon) {

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

    /** An objective on an offer: nothing started, nothing wrong. */
    public static CardObjective offered(Component text, int required, ItemStack icon) {
        return new CardObjective(text, 0, required, State.PENDING, icon);
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
    }

    public static CardObjective decode(FriendlyByteBuf buf) {
        return new CardObjective(
                buf.readComponent(),
                buf.readVarInt(),
                buf.readVarInt(),
                State.byOrdinal(buf.readVarInt()),
                buf.readItem());
    }
}
