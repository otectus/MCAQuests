package dev.otectus.mcaquests.compat;

import java.util.List;

/**
 * A Townstead root — the species/ancestry/lineage a villager descends from (Townstead spec §2.3).
 *
 * <p>{@link #effectiveSpecies()} is the resolved species after inheritance, which is what content
 * should gate on; {@link #species()} is what this root declares directly and may be empty for a
 * root that inherits one.
 */
public record TownsteadRootView(
        String id,
        String displayName,
        String species,
        String ancestry,
        String lineage,
        String effectiveSpecies,
        List<String> defaultGenes,
        List<TownsteadLifeStageView> lifeStages) {

    public TownsteadRootView {
        defaultGenes = List.copyOf(defaultGenes);
        lifeStages = List.copyOf(lifeStages);
    }
}
