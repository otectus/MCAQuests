package dev.otectus.mcaquests.quest.reputation;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only registry of reputation tier ladders, swapped atomically by {@code ReputationTierLoader} on
 * datapack reload (mirrors {@code ProjectRegistry}). Ladders are keyed by {@link ResourceLocation}; the
 * condition and UI default to {@link #DEFAULT_ID}. A hardcoded {@link #BUILTIN_DEFAULT} guarantees the
 * system still works if a pack removes the default datapack file.
 */
public final class ReputationTiers {

    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "default");

    public static final ReputationTierSet BUILTIN_DEFAULT = new ReputationTierSet(List.of(
            new ReputationTier("stranger", 0, "Stranger", Optional.empty()),
            new ReputationTier("acquaintance", 25, "Acquaintance", Optional.empty()),
            new ReputationTier("friend", 75, "Friend", Optional.empty()),
            new ReputationTier("honored", 150, "Honored",
                    Optional.of(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "honored_of_village"))),
            new ReputationTier("revered", 300, "Revered",
                    Optional.of(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "revered_of_village")))));

    private static volatile Map<ResourceLocation, ReputationTierSet> ladders = Map.of();

    private ReputationTiers() {
    }

    public static void replaceAll(Map<ResourceLocation, ReputationTierSet> loaded) {
        ladders = Map.copyOf(loaded);
    }

    public static Optional<ReputationTierSet> get(ResourceLocation id) {
        return Optional.ofNullable(ladders.get(id));
    }

    /** Every loaded ladder id (task M5.1: FTB editor known-ids sync, spec §20). */
    public static Set<ResourceLocation> ids() {
        return ladders.keySet();
    }

    /** The active default ladder, falling back to {@link #BUILTIN_DEFAULT} when none is loaded. */
    public static ReputationTierSet getDefault() {
        return ladders.getOrDefault(DEFAULT_ID, BUILTIN_DEFAULT);
    }

    /** The named ladder if loaded, otherwise the default ladder (never null). */
    public static ReputationTierSet getOrDefault(ResourceLocation id) {
        ReputationTierSet set = ladders.get(id);
        return set != null ? set : getDefault();
    }
}
