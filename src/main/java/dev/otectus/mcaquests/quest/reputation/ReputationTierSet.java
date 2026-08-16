package dev.otectus.mcaquests.quest.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * An ordered ladder of {@link ReputationTier}s (spec 0.7.0), applied to a village's flat reputation
 * value. All lookup helpers are pure so they can be unit-tested without game registries. Tiers are
 * expected to ascend strictly by threshold (enforced by {@code ReputationTierValidator}); the lookups
 * here tolerate a well-formed ladder and assume a non-empty list.
 */
public record ReputationTierSet(List<ReputationTier> tiers) {

    public static final Codec<ReputationTierSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ReputationTier.CODEC.listOf().fieldOf("tiers").forGetter(ReputationTierSet::tiers)
    ).apply(instance, ReputationTierSet::new));

    public boolean isEmpty() {
        return tiers.isEmpty();
    }

    /** The highest tier whose threshold is &le; {@code rep}; the lowest tier acts as the floor. */
    public ReputationTier tierFor(int rep) {
        ReputationTier match = tiers.get(0);
        for (ReputationTier tier : tiers) {
            if (rep >= tier.threshold()) {
                match = tier;
            } else {
                break;
            }
        }
        return match;
    }

    /** Ladder index of the tier with this id, or {@code -1} when no such tier exists. */
    public int indexOf(String tierId) {
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(tierId)) {
                return i;
            }
        }
        return -1;
    }

    public Optional<ReputationTier> byId(String tierId) {
        int index = indexOf(tierId);
        return index < 0 ? Optional.empty() : Optional.of(tiers.get(index));
    }

    /** The tier immediately above the one containing {@code rep}, if any (none when already at the top). */
    public Optional<ReputationTier> nextTier(int rep) {
        int index = indexOf(tierFor(rep).id());
        return index + 1 < tiers.size() ? Optional.of(tiers.get(index + 1)) : Optional.empty();
    }
}
