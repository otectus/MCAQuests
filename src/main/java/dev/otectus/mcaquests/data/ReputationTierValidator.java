package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural validation for reputation tier ladders (spec 0.7.0): non-empty, unique tier ids, strictly
 * ascending thresholds, and a floor at {@code <= 0} so every reputation value maps to a tier. Returns
 * {@code true} when the ladder is usable; appends human-readable problems to {@code errors} otherwise.
 */
public final class ReputationTierValidator {

    private ReputationTierValidator() {
    }

    public static boolean validate(ResourceLocation id, ReputationTierSet set, List<String> errors) {
        List<ReputationTier> tiers = set.tiers();
        if (tiers.isEmpty()) {
            errors.add("Reputation tier set '" + id + "' has no tiers.");
            return false;
        }
        boolean ok = true;
        Set<String> seen = new HashSet<>();
        int previous = Integer.MIN_VALUE;
        for (ReputationTier tier : tiers) {
            if (!seen.add(tier.id())) {
                errors.add("Reputation tier set '" + id + "' has duplicate tier id '" + tier.id() + "'.");
                ok = false;
            }
            if (tier.threshold() <= previous) {
                errors.add("Reputation tier set '" + id + "' thresholds must strictly ascend; '"
                        + tier.id() + "' (" + tier.threshold() + ") is not greater than the previous (" + previous + ").");
                ok = false;
            }
            previous = tier.threshold();
        }
        if (tiers.get(0).threshold() > 0) {
            errors.add("Reputation tier set '" + id + "' lowest tier threshold must be <= 0 to act as a floor (got "
                    + tiers.get(0).threshold() + ").");
            ok = false;
        }
        return ok;
    }
}
