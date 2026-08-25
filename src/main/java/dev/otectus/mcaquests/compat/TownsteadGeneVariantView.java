package dev.otectus.mcaquests.compat;

/** One selectable variant of a Townstead gene (Townstead spec §2.3). */
public record TownsteadGeneVariantView(String id, String displayName, int weight, String type) {
}
