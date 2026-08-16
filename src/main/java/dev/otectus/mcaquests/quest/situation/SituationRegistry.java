package dev.otectus.mcaquests.quest.situation;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server-side set of currently-loaded situation definitions, swapped atomically by
 * {@code SituationDataLoader} on every datapack reload (0.8.0). Read-only to the rest of the mod, with
 * a secondary index from each definition's {@link SituationDefinition#syntheticId()} so accepted
 * situation offers can be resolved back to their base quest definition.
 */
public final class SituationRegistry {

    private static volatile Map<ResourceLocation, SituationDefinition> situations = Map.of();
    private static volatile Map<ResourceLocation, SituationDefinition> bySyntheticId = Map.of();
    private static volatile List<String> lastErrors = List.of();
    private static volatile List<String> lastWarnings = List.of();

    private SituationRegistry() {
    }

    public static void replaceAll(Map<ResourceLocation, SituationDefinition> loaded,
                                  List<String> errors, List<String> warnings) {
        Map<ResourceLocation, SituationDefinition> synthetic = new LinkedHashMap<>();
        for (SituationDefinition def : loaded.values()) {
            synthetic.put(def.syntheticId(), def);
        }
        situations = Map.copyOf(loaded);
        bySyntheticId = Map.copyOf(synthetic);
        lastErrors = List.copyOf(errors);
        lastWarnings = List.copyOf(warnings);
    }

    public static Collection<SituationDefinition> all() {
        return situations.values();
    }

    public static Optional<SituationDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(situations.get(id));
    }

    /** Looks up a definition by its synthesized offer-quest id (see {@link SituationDefinition#syntheticId()}). */
    public static Optional<SituationDefinition> getBySyntheticId(ResourceLocation syntheticId) {
        return Optional.ofNullable(bySyntheticId.get(syntheticId));
    }

    public static boolean contains(ResourceLocation id) {
        return situations.containsKey(id);
    }

    public static int size() {
        return situations.size();
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    public static List<String> lastWarnings() {
        return lastWarnings;
    }
}
