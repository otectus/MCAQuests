package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * An authored question about a player's public profile, in Minecraft and Java types only.
 *
 * <p>The Quests-side mirror of MCA: Reputation 0.6.0's {@code ProfileQuery} (spec §14.5). It exists
 * for the same reason {@link IncidentSelector} does: the condition that carries it
 * ({@code mcareputation:profile}) is parsed and registered on every installation, so it must not name
 * a Reputation type. {@code CanonicalReputationBackend} translates this into the real query behind the
 * mod-present gate.
 *
 * <h2>Semantics, which are Reputation's and not ours</h2>
 *
 * <p>Every clause is ANDed. An unknown facet or recognition tier id fails closed — a datapack typo
 * must not open a gate. A facet clause requires live evidence ({@link Facet#minEvidence()} defaults to
 * 1), because a valid-but-unobserved facet is <b>not</b> negative evidence: a village that has simply
 * never seen the player must not satisfy "nonviolent". {@link Facet#allowUnobserved()} is the named
 * escape hatch for "no contrary evidence is known", and it makes the clause depend on complete
 * history, as does any upper bound.
 *
 * @param scope               whose view is being asked about
 * @param minRecognition      inclusive lower bound on recognition, if any
 * @param maxRecognition      inclusive upper bound on recognition, if any
 * @param minRecognitionTier  a named rung of the recognition ladder the player must have reached
 * @param facets              per-facet bounds, at most 16 and at most one clause per facet
 * @param allowPartialHistory whether a clause that needs complete history may answer on a save
 *                            migrated from an older format
 */
public record ReputationProfileQuery(Scope scope, OptionalInt minRecognition, OptionalInt maxRecognition,
                                     Optional<String> minRecognitionTier, List<Facet> facets,
                                     boolean allowPartialHistory) {

    /** At most sixteen facet clauses, matching Reputation's own bound so a query never fails there. */
    public static final int MAX_FACETS = 16;

    /** Whose knowledge answers the question. */
    public enum Scope {

        /**
         * What the village as a whole can say. The right scope for "is this player known here at all".
         */
        COMMUNITY,
        /**
         * What the quest giver personally knows, filtered through their own awareness and the delay
         * before rumour reaches them. Never widens to the community view: a giver who knows nothing
         * answers "no", and a giver who cannot be resolved answers "cannot say" so the authored
         * fallback runs.
         */
        GIVER;

        public String jsonName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One facet clause.
     *
     * @param facet          the facet id, e.g. {@code mcareputation:reliability}
     * @param min            inclusive lower bound on the facet value
     * @param max            inclusive upper bound on the facet value
     * @param minEvidence    how many live evidence items must support it (default 1)
     * @param allowUnobserved when true, an unobserved facet satisfies the clause — "nothing is known
     *                        against them", which is a different claim from "they are known for this"
     */
    public record Facet(ResourceLocation facet, OptionalInt min, OptionalInt max, int minEvidence,
                        boolean allowUnobserved) {

        public Facet {
            min = min == null ? OptionalInt.empty() : min;
            max = max == null ? OptionalInt.empty() : max;
            minEvidence = Math.max(0, minEvidence);
        }

        public static Facet atLeast(ResourceLocation facet, int min) {
            return new Facet(facet, OptionalInt.of(min), OptionalInt.empty(), 1, false);
        }

        /** True when this clause relies on absence of evidence, and so on a complete history. */
        public boolean dependsOnCompleteHistory() {
            return max.isPresent() || allowUnobserved;
        }
    }

    public ReputationProfileQuery {
        scope = scope == null ? Scope.COMMUNITY : scope;
        minRecognition = minRecognition == null ? OptionalInt.empty() : minRecognition;
        maxRecognition = maxRecognition == null ? OptionalInt.empty() : maxRecognition;
        minRecognitionTier = minRecognitionTier == null ? Optional.empty() : minRecognitionTier;
        facets = facets == null ? List.of() : List.copyOf(facets);
    }

    public static ReputationProfileQuery community() {
        return new ReputationProfileQuery(Scope.COMMUNITY, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty(), List.of(), false);
    }

    /** True when this asks nothing at all, which an authored condition must refuse rather than pass. */
    public boolean isEmpty() {
        return minRecognition.isEmpty() && maxRecognition.isEmpty() && minRecognitionTier.isEmpty()
                && facets.isEmpty();
    }

    /**
     * Whether this query is structurally answerable at all: bounds the right way round, no duplicated
     * or malformed facet id, and inside Reputation's own clause bound.
     *
     * <p>Checked on the Quests side as well as inside Reputation, because a query Reputation rejects
     * comes back "cannot say" — correct, but a validation error at pack-load time is far more use to
     * the author than an unmet condition at run time.
     */
    public boolean valid() {
        if (facets.size() > MAX_FACETS) {
            return false;
        }
        if (minRecognition.isPresent() && minRecognition.getAsInt() < 0) {
            return false;
        }
        if (maxRecognition.isPresent() && maxRecognition.getAsInt() < 0) {
            return false;
        }
        if (minRecognition.isPresent() && maxRecognition.isPresent()
                && minRecognition.getAsInt() > maxRecognition.getAsInt()) {
            return false;
        }
        if (minRecognitionTier.isPresent() && minRecognitionTier.get().isBlank()) {
            return false;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Facet facet : facets) {
            if (facet == null || facet.facet() == null) {
                return false;
            }
            if (facet.min().isPresent() && facet.max().isPresent()
                    && facet.min().getAsInt() > facet.max().getAsInt()) {
                return false;
            }
            if (!seen.add(facet.facet().toString())) {
                return false;
            }
        }
        return true;
    }

    /** True when any clause relies on the absence of evidence, and so on a complete history. */
    public boolean dependsOnCompleteHistory() {
        if (maxRecognition.isPresent()) {
            return true;
        }
        for (Facet facet : facets) {
            if (facet.dependsOnCompleteHistory()) {
                return true;
            }
        }
        return false;
    }

    /** A copy with one more facet clause. */
    public ReputationProfileQuery with(Facet facet) {
        List<Facet> merged = new ArrayList<>(facets);
        merged.add(facet);
        return new ReputationProfileQuery(scope, minRecognition, maxRecognition, minRecognitionTier,
                merged, allowPartialHistory);
    }
}
