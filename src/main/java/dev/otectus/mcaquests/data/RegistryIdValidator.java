package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.BiomeCondition;
import dev.otectus.mcaquests.quest.condition.leaf.DimensionCondition;
import dev.otectus.mcaquests.quest.objective.EnterStructureObjective;
import dev.otectus.mcaquests.quest.objective.FindMissingRelativeObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.VisitBiomeObjective;
import dev.otectus.mcaquests.quest.objective.VisitDimensionObjective;
import dev.otectus.mcaquests.quest.target.BiomeTarget;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Checks the biome, dimension and structure ids a quest names against the registries of the
 * <em>running world</em>, as warnings.
 *
 * <p>All three live in datapack-driven dynamic registries, so a codec cannot check them: at parse time
 * there is nothing to check against. What that costs is quiet — {@code BiomeTarget.matches} and
 * {@code StructureTarget.matches} answer "no" forever for an id nothing defines, and
 * {@code visit_dimension} compares two {@link ResourceLocation}s that will never be equal, so a typo
 * (or a quest written for a mod that is not installed) becomes a quest that never advances and never
 * says why. The earliest anyone can ask is against a running server, which is where
 * {@code /mcaquests validate} lives.
 *
 * <p>Warnings, never errors: a pack may legitimately name a biome from a mod the admin has not
 * installed today, and taking the server down over it would be worse than the objective being dead.
 * A registry that cannot be read at all is reported as fine, matching {@code BiomeTarget.isKnown}.
 */
public final class RegistryIdValidator {

    private RegistryIdValidator() {
    }

    public static List<String> collectWarnings(RegistryAccess registries, Collection<QuestDefinition> quests) {
        List<String> warnings = new ArrayList<>();
        for (QuestDefinition def : quests) {
            String label = "Quest '" + def.id() + "'";
            List<QuestObjective> objectives = def.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                checkObjective(registries, label + " objective[" + i + "]", objectives.get(i), warnings);
            }
            def.effectiveConditions().ifPresent(condition ->
                    checkCondition(registries, label + " conditions", condition, warnings));
        }
        return warnings;
    }

    private static void checkObjective(RegistryAccess registries, String where, QuestObjective objective,
                                       List<String> warnings) {
        if (objective instanceof VisitBiomeObjective visit) {
            checkBiome(registries, where, visit.target(), warnings);
        } else if (objective instanceof EnterStructureObjective enter) {
            checkStructure(registries, where, enter.structure(), warnings);
        } else if (objective instanceof VisitDimensionObjective visit) {
            checkDimension(registries, where, visit.dimension(), warnings);
        } else if (objective instanceof FindMissingRelativeObjective find) {
            find.biome().ifPresent(biome -> checkBiome(registries, where, biome, warnings));
            find.structure().ifPresent(structure -> checkStructure(registries, where, structure, warnings));
        }
    }

    /** Walks composites too: an id inside an {@code any_of} branch is just as unresolvable. */
    private static void checkCondition(RegistryAccess registries, String where, QuestCondition condition,
                                       List<String> warnings) {
        if (condition instanceof AllOfCondition all) {
            all.conditions().forEach(child -> checkCondition(registries, where, child, warnings));
        } else if (condition instanceof AnyOfCondition any) {
            any.conditions().forEach(child -> checkCondition(registries, where, child, warnings));
        } else if (condition instanceof NotCondition not) {
            checkCondition(registries, where, not.condition(), warnings);
        } else if (condition instanceof BiomeCondition biome) {
            checkBiome(registries, where, biome.target(), warnings);
        } else if (condition instanceof DimensionCondition dimension) {
            checkDimension(registries, where, dimension.dimension(), warnings);
        }
    }

    private static void checkBiome(RegistryAccess registries, String where, BiomeTarget target,
                                   List<String> warnings) {
        Optional<Registry<Biome>> registry = registry(registries, Registries.BIOME);
        if (registry.isEmpty()) {
            return;
        }
        target.biome()
                .filter(id -> !registry.get().containsKey(ResourceKey.create(Registries.BIOME, id)))
                .ifPresent(id -> warnings.add(where + " names biome '" + id
                        + "', which this world's registries do not define; it can never match."));
        target.tag()
                .filter(tag -> isEmptyTag(registry.get(), tag))
                .ifPresent(tag -> warnings.add(where + " names biome tag '" + tag.location()
                        + "', which is unknown or empty here; it can never match."));
    }

    private static void checkStructure(RegistryAccess registries, String where, StructureTarget target,
                                       List<String> warnings) {
        Optional<Registry<Structure>> registry = registry(registries, Registries.STRUCTURE);
        if (registry.isEmpty()) {
            return;
        }
        target.structure()
                .filter(id -> !registry.get().containsKey(ResourceKey.create(Registries.STRUCTURE, id)))
                .ifPresent(id -> warnings.add(where + " names structure '" + id
                        + "', which this world's registries do not define; it can never match."));
        target.tag()
                .filter(tag -> isEmptyTag(registry.get(), tag))
                .ifPresent(tag -> warnings.add(where + " names structure tag '" + tag.location()
                        + "', which is unknown or empty here; it can never match."));
    }

    /**
     * Dimensions are checked against {@code LEVEL_STEM}, the registry that decides which levels a world
     * actually has — {@code Registries.DIMENSION} is only the key space those levels are named in, so
     * every well-formed id "exists" in it.
     */
    private static void checkDimension(RegistryAccess registries, String where, ResourceLocation dimension,
                                       List<String> warnings) {
        Optional<Registry<LevelStem>> registry = registry(registries, Registries.LEVEL_STEM);
        if (registry.isEmpty()) {
            return;
        }
        if (!registry.get().containsKey(ResourceKey.create(Registries.LEVEL_STEM, dimension))) {
            warnings.add(where + " names dimension '" + dimension
                    + "', which this world does not have; it can never be entered.");
        }
    }

    private static <T> boolean isEmptyTag(Registry<T> registry, TagKey<T> tag) {
        return registry.getTag(tag).map(named -> named.size() == 0).orElse(true);
    }

    /** A registry that is missing entirely means "cannot tell", which is reported as nothing at all. */
    private static <T> Optional<Registry<T>> registry(RegistryAccess registries, ResourceKey<Registry<T>> key) {
        try {
            return Optional.of(registries.registryOrThrow(key));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
