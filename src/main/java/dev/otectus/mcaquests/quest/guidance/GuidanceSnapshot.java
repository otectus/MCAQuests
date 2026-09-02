package dev.otectus.mcaquests.quest.guidance;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every place the player is being sent, and which of them the world marker stands on.
 *
 * <p>Guidance began as one answer per player, and the tracker showed a destination for exactly one
 * quest — the one the marker had chosen. Every other row fell back to naming a villager, or to
 * nothing at all, so a player holding "enter an ancient city" and "kill eight blazes in a fortress"
 * was told where one of them was and left to guess about the other. Both answers existed on the
 * server; only one was ever sent.
 *
 * <p>So the list is the payload now, and the marker is one entry of it. That split is the whole
 * point: <b>every quest gets a destination, and there is still only ever one beam.</b> Drawing a
 * marker per quest would be five beacons for five quests, which is the mistake highlighting used to
 * make and which {@code QuestMarkerRenderer} is explicitly built not to repeat.
 *
 * @param all     one entry per active quest that can say where to go, in quest-log order. A quest
 *                with nothing to point at is absent rather than present-and-empty
 * @param primary index into {@link #all} of the entry the marker and the villager outline are about,
 *                or {@code -1} when there is nothing to mark. An index rather than a repeated record
 *                so the two can never disagree, and so the wire does not carry a target twice
 */
public record GuidanceSnapshot(List<ActiveGuidance> all, int primary) {

    /** Nothing to point at anywhere. A real message, not the absence of one: it takes a marker away. */
    public static final GuidanceSnapshot EMPTY = new GuidanceSnapshot(List.of(), -1);

    public GuidanceSnapshot {
        all = List.copyOf(all);
        primary = primary >= 0 && primary < all.size() ? primary : -1;
    }


    /**
     * Builds a snapshot from one answer per active quest, in quest-log order.
     *
     * <p>The whole selection rule, as a pure function over answers that have already been computed —
     * which is what makes it testable at all, and is why it lives here rather than inside
     * {@code GuidanceService}'s walk over live server objects.
     *
     * <p>Two rules, and the second one is a bug fix wearing a rule's clothes:
     *
     * <ol>
     *   <li><b>A quest with nothing to say is left out, not left empty.</b> The tracker draws a
     *       destination under the rows that have one and says nothing under the rest.</li>
     *   <li><b>An empty answer never ends the search.</b> The followed quest takes the marker when it
     *       can answer; otherwise the first quest that can does. A pin is a preference, not a vow of
     *       silence — and the version of this that stopped at the first quest it asked meant one
     *       escort with an unresolved destination could switch the marker off for every other quest
     *       the player held.</li>
     * </ol>
     *
     * @param answers      one entry per active quest, in order; empty where the quest cannot say
     * @param trackedIndex index into {@code answers} of the quest the player is following, or any
     *                     out-of-range value when they are following nothing
     */
    public static GuidanceSnapshot select(List<Optional<ActiveGuidance>> answers, int trackedIndex) {
        List<ActiveGuidance> present = new ArrayList<>(answers.size());
        int primary = -1;
        for (int i = 0; i < answers.size(); i++) {
            Optional<ActiveGuidance> answer = answers.get(i);
            if (answer.isEmpty()) {
                continue;
            }
            if (primary < 0 || i == trackedIndex) {
                primary = present.size();
            }
            present.add(answer.get());
        }
        return present.isEmpty() ? EMPTY : new GuidanceSnapshot(present, primary);
    }

    /** The entry the marker is about, or empty. */
    public Optional<ActiveGuidance> primaryGuidance() {
        return primary < 0 ? Optional.empty() : Optional.of(all.get(primary));
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public static void encode(FriendlyByteBuf buf, GuidanceSnapshot snapshot) {
        buf.writeCollection(snapshot.all, ActiveGuidance::encode);
        buf.writeVarInt(snapshot.primary + 1); // +1 so "none" is 0 rather than a 5-byte negative
    }

    public static GuidanceSnapshot decode(FriendlyByteBuf buf) {
        List<ActiveGuidance> all = buf.readCollection(ArrayList::new, ActiveGuidance::decode);
        return new GuidanceSnapshot(all, buf.readVarInt() - 1);
    }
}
