package dev.otectus.mcaquests.compat;

import java.util.List;

/**
 * What a Townstead profession's progression can actually reach (spec §5.1).
 *
 * <p>This exists because of a defect that shipped in 1.4.0. Townstead's registry answers
 * {@code spec(professionId)} for <em>every</em> profession, including ones it has no progression for
 * at all — those get a zero/default spec back rather than nothing. Content that asked a fisherman to
 * earn 120 profession XP therefore parsed, validated, was offered, was accepted, and then waited
 * forever, because no fisherman work task in Townstead 0.7.6 awards a single point. A quest that can
 * never finish is worse than a quest that never appears.
 *
 * <p>So a track is <b>not progressive</b> when it exposes only the zero/default tier and both the
 * daily cap and the maximum XP are zero. That is the exact shape of Townstead's fallback, and it is
 * the one thing this whole view exists to tell apart from a real track. Everything that gates on
 * profession progress — the offer conditions, the objective, the workforce project, the validator —
 * asks {@link #progressive()} first.
 *
 * <p><b>MCA: Quests owns this record.</b> Nothing Townstead-typed reaches it; the reflective bridge
 * converts on the way out, which is what lets a datapack-supplied fisherman track work with no code
 * change here.
 *
 * @param professionId    the id this track was resolved under, canonicalised to what the caller asked
 * @param tierThresholds  the minimum XP for tiers 1..{@link #maxTier()}, ascending; empty when the
 *                        track is not progressive or the thresholds could not be derived
 * @param maxTier         the highest tier reachable at {@link #maxXp()}, from Townstead's own tier
 *                        calculation rather than from the length of any threshold array
 * @param maxXp           the XP ceiling, {@code 0} for a non-progressive track
 * @param dailyCap        Townstead's per-day XP cap, {@code 0} when it imposes none or has no track
 * @param dataDriven      true only when Townstead's data-driven profession registry could be read
 *                        <em>and</em> knows this id; false also means "could not tell", so nothing
 *                        may treat it as proof that a track is built in
 */
public record TownsteadProfessionTrackView(
        String professionId,
        List<Integer> tierThresholds,
        int maxTier,
        int maxXp,
        int dailyCap,
        boolean dataDriven) {

    public TownsteadProfessionTrackView {
        tierThresholds = List.copyOf(tierThresholds);
    }

    /** The value a bound-but-unknown profession resolves to: readable, and provably going nowhere. */
    public static TownsteadProfessionTrackView none(String professionId) {
        return new TownsteadProfessionTrackView(professionId, List.of(), 0, 0, 0, false);
    }

    /**
     * True when this profession can actually advance. Townstead's default spec reports tier 0, no
     * ceiling and no cap; anything that can raise a tier or bank XP reports more than that.
     */
    public boolean progressive() {
        return maxTier > 0 && maxXp > 0;
    }

    /** True when a quest may ask this profession to reach {@code tier}. */
    public boolean supportsTier(int tier) {
        return progressive() && tier > 0 && tier <= maxTier;
    }

    /**
     * True when {@code delta} more XP is still reachable from {@code currentXp}. A villager already at
     * the ceiling cannot earn another point, so a quest asking for one would hang exactly the way
     * 1.4.0's fisherman quests did.
     */
    public boolean supportsXpDelta(int currentXp, int delta) {
        return progressive() && delta > 0 && remainingXp(currentXp) >= delta;
    }

    /** How much XP is still available above {@code currentXp}; never negative. */
    public int remainingXp(int currentXp) {
        return Math.max(0, maxXp - Math.max(0, currentXp));
    }

    /** The XP at which {@code tier} begins, or empty when this track cannot reach it. */
    public java.util.OptionalInt thresholdFor(int tier) {
        if (!supportsTier(tier) || tier > tierThresholds.size()) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(tierThresholds.get(tier - 1));
    }
}
