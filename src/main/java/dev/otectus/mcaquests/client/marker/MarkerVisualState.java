package dev.otectus.mcaquests.client.marker;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * How solid the marker is because of <em>when</em> it appeared, as opposed to where it is.
 *
 * <p>Distance decides most of the marker's opacity; this decides the rest. Without it every change of
 * objective is a hard cut — one marker vanishes and another exists — which reads as a glitch rather
 * than as progress, and is the single thing that makes an otherwise stationary marker look unstable.
 *
 * <p>Opacity only. Nothing here moves, scales or rotates anything: a marker that slides into place is
 * a marker whose position you cannot trust, which is the opposite of what it is for.
 *
 * <p>Time is passed in rather than read, so the whole thing is testable without a running game.
 */
public final class MarkerVisualState {

    /** How long a new target takes to reach full strength, in milliseconds. */
    public static final long ACQUIRE_MS = 160L;
    /** How long a target that has gone away takes to fade out, in milliseconds. */
    public static final long CLEAR_MS = 120L;

    private Key current;
    private long currentStart;

    private Key previous;
    private long previousStart;
    private float previousBase;

    /** Whether the last caller asked for reduced motion; remembered so {@link #clear} obeys it too. */
    private boolean reducedMotion;

    /**
     * What makes one marker a different marker.
     *
     * <p>The quest and its giver, because the same quest from two villagers is two objectives; and
     * the target identity, because one quest walking the player from a bed to a workstation is a
     * retarget even though the quest never changed. A villager's own movement is none of these, so
     * following somebody who walks does not restart the fade every time the server speaks.
     *
     * @param targetIdentity the entity's network id, or the target position's, per
     *                       {@code QuestMarkerRenderer}
     */
    public record Key(ResourceLocation questId, UUID villager, int targetIdentity) {
    }

    /**
     * Tell the state which target is current, as of {@code nowMillis}.
     *
     * @return true when this is a different target from the one before it, so the caller can hold on
     *         to what it was drawing and let it fade out
     */
    public boolean observe(Key key, long nowMillis, boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        if (Objects.equals(current, key)) {
            return false;
        }
        boolean retarget = current != null;
        if (retarget && !reducedMotion) {
            previous = current;
            previousStart = nowMillis;
            previousBase = lifetimeAlpha(nowMillis);
        } else {
            previous = null;
            previousBase = 0.0F;
        }
        current = key;
        // Reduced motion is not "a shorter fade": it is no fade. Starting a full acquire ago is how
        // the same arithmetic answers 1 immediately without a second branch in every reader.
        currentStart = reducedMotion ? nowMillis - ACQUIRE_MS : nowMillis;
        return retarget;
    }

    /**
     * Tell the state there is no target any more.
     *
     * @return true when there was one a moment ago, so the caller can keep drawing it while it goes
     */
    public boolean clear(long nowMillis) {
        if (current == null) {
            return false;
        }
        if (!reducedMotion) {
            previous = current;
            previousStart = nowMillis;
            previousBase = lifetimeAlpha(nowMillis);
        } else {
            previous = null;
            previousBase = 0.0F;
        }
        current = null;
        return true;
    }

    /** Forget everything, with no fade: the marker was switched off, not completed. */
    public void reset() {
        current = null;
        previous = null;
        previousBase = 0.0F;
    }

    /** How solid the current target is, from its acquire alone. */
    public float lifetimeAlpha(long nowMillis) {
        if (current == null) {
            return 0.0F;
        }
        double t = (double) (nowMillis - currentStart) / ACQUIRE_MS;
        return (float) MarkerGeometry.easeOutCubic(t);
    }

    /** How solid the target that was just replaced still is, as it goes. */
    public float previousAlpha(long nowMillis) {
        if (previous == null) {
            return 0.0F;
        }
        double t = (double) (nowMillis - previousStart) / CLEAR_MS;
        return (float) (previousBase * (1.0D - MarkerGeometry.smoothstep(0.0D, 1.0D, t)));
    }

    /** The target being drawn, or null when there is none. */
    public Key current() {
        return current;
    }
}
