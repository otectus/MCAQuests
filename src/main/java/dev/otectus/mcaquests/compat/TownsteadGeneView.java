package dev.otectus.mcaquests.compat;

import java.util.List;

/**
 * A Townstead gene definition (Townstead spec §2.3). {@code dominance} and {@code displayMode} are
 * lowercase strings for the same reason as elsewhere in this package: no Townstead enum crosses the
 * boundary.
 */
public record TownsteadGeneView(
        String id,
        String displayName,
        String description,
        String category,
        String dominance,
        String locus,
        int weight,
        String displayMode,
        List<TownsteadGeneVariantView> variants) {

    public TownsteadGeneView {
        variants = List.copyOf(variants);
    }
}
