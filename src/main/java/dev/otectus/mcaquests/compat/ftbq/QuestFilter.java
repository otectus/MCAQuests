package dev.otectus.mcaquests.compat.ftbq;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Function;

/**
 * String-matching helper for the FTB Quests history tasks (spec §15.0-15.1): given
 * ({@code questIdPattern}, {@code profession}, {@code chainId}, {@code category}) it decides whether a
 * history entry (a completed quest id) matches. Lives in {@code compat.ftbq} because it is only used
 * by the FTBQ history tasks, but deliberately imports nothing from {@code dev.ftb.mods} — it is pure
 * game-logic + config-enum plumbing, so it loads and tests fine with no FTB jars present (verified by
 * {@code NoFtbqClassloadTest} and by this class's own unit test running with no FTB on the classpath).
 *
 * <p><b>Resolver seam:</b> matching needs the {@link QuestDefinition} behind a completed id — including
 * the situation-offer synthetic-id case, where {@code QuestDefinitions.resolve} synthesizes an offer
 * definition on the fly. Rather than calling {@code QuestDefinitions.resolve} statically (which would
 * make this class depend on the live, datapack-populated registry and be hard to unit test),
 * {@link #matches} takes the resolver as a {@code Function<ResourceLocation, Optional<QuestDefinition>>}
 * parameter. Production callers pass {@code QuestDefinitions::resolve}; tests pass a small stub map,
 * including one keyed by a synthetic situation id mapping to an offer definition with a different real
 * id — proving id-pattern matching runs against the <em>resolved definition's id</em>, not the raw
 * history id (so a datapack author can write {@code quest_id: mcaquests:some_offer} and have it match
 * situation-offer completions too, per §15.1).
 *
 * <p><b>Unresolvable entries:</b> per §15.1, "entries whose definition no longer resolves match only
 * the 'any' pattern" — interpreted here as: an entry with no resolvable definition can still satisfy a
 * filter, but only the fully-wildcard filter (every field empty), since profession/chain/category
 * checks need a definition to evaluate and questIdPattern is matched against the definition's id, which
 * does not exist either.
 */
public record QuestFilter(String questIdPattern, String profession, String chainId, String category) {

    /** True when every field is the empty/"any" value. */
    public boolean isAny() {
        return questIdPattern.isEmpty() && profession.isEmpty() && chainId.isEmpty() && category.isEmpty();
    }

    /**
     * Whether the completion of {@code completedQuestId} matches this filter. {@code resolver}
     * resolves the id to its {@link QuestDefinition} (production: {@code QuestDefinitions::resolve});
     * {@code mode} is the configured {@link ProfessionMatchingMode}. All four fields compose with AND
     * (§15.1); an empty field is a wildcard for that dimension.
     */
    public boolean matches(ResourceLocation completedQuestId,
                            Function<ResourceLocation, Optional<QuestDefinition>> resolver,
                            ProfessionMatchingMode mode) {
        Optional<QuestDefinition> resolved = resolver.apply(completedQuestId);
        if (resolved.isEmpty()) {
            return isAny();
        }
        QuestDefinition definition = resolved.get();
        return matchesIdPattern(definition.id())
                && matchesProfession(definition, mode)
                && matchesChain(definition)
                && matchesCategory(definition);
    }

    private boolean matchesIdPattern(ResourceLocation definitionId) {
        if (questIdPattern.isEmpty()) {
            return true;
        }
        if (questIdPattern.endsWith(":*")) {
            String namespace = questIdPattern.substring(0, questIdPattern.length() - 2);
            return definitionId.getNamespace().equals(namespace);
        }
        return definitionId.toString().equals(questIdPattern);
    }

    private boolean matchesProfession(QuestDefinition definition, ProfessionMatchingMode mode) {
        if (profession.isEmpty()) {
            return true;
        }
        ResourceLocation actual = ResourceLocation.tryParse(profession);
        if (actual == null) {
            return false;
        }
        return definition.giver().isGeneric()
                || ProfessionMatcher.matchesAny(definition.giver().professions(), actual, mode);
    }

    private boolean matchesChain(QuestDefinition definition) {
        if (chainId.isEmpty()) {
            return true;
        }
        return definition.chain().map(chain -> chain.chain().equals(chainId)).orElse(false);
    }

    private boolean matchesCategory(QuestDefinition definition) {
        if (category.isEmpty()) {
            return true;
        }
        return definition.category().map(category::equals).orElse(false);
    }
}
