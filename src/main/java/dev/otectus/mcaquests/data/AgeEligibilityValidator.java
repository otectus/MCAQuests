package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.AgeGroupCondition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Warns about a quest that opts out of the adult-only giver gate without saying which ages it is
 * written for.
 *
 * <p>{@code "adult_only": false} removes the <em>only</em> age restriction MCA: Quests applies by
 * default, so every MCA age state — {@code baby} and {@code toddler} included — becomes an eligible
 * giver. A quest written in a child's voice then gets offered by an infant. Pairing it with an
 * {@code age_group} condition states the intent explicitly.
 *
 * <p>This only ever warns: a datapack is free to mean it (a wordless "bring me a flower" errand from a
 * toddler is perfectly reasonable), and turning it into a load error would break existing third-party
 * packs. The shipped pack is held to the stricter rule by {@code BuiltinAgeEligibilityTest}.
 */
public final class AgeEligibilityValidator {

    /** Ages with no written dialogue in MCA — a quest line from one of these reads as a bug. */
    private static final Set<String> NON_SPEAKING = Set.of("baby", "toddler");

    private AgeEligibilityValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> warnings) {
        for (QuestDefinition def : loaded.values()) {
            if (def.giver().adultOnly()) {
                continue;
            }
            List<String> groups = collectAgeGroups(def.conditions());
            if (groups.isEmpty()) {
                warnings.add("Quest '" + def.id() + "' sets adult_only:false but has no age_group condition, so "
                        + "babies and toddlers can offer it. Add an age_group condition (combined with any "
                        + "existing conditions via all_of) naming the ages this quest is written for.");
                continue;
            }
            List<String> nonSpeaking = groups.stream().filter(NON_SPEAKING::contains).sorted().distinct().toList();
            if (!nonSpeaking.isEmpty()) {
                warnings.add("Quest '" + def.id() + "' allows age group(s) " + nonSpeaking
                        + ", which have no spoken dialogue in MCA. Check the quest text reads sensibly from one.");
            }
        }
    }

    /** Every {@code age_group} value reachable in the condition tree, descending through composites. */
    private static List<String> collectAgeGroups(Optional<QuestCondition> conditions) {
        return conditions.map(AgeEligibilityValidator::walk).orElse(List.of());
    }

    private static List<String> walk(QuestCondition condition) {
        if (condition instanceof AgeGroupCondition age) {
            return age.groups();
        }
        if (condition instanceof AllOfCondition all) {
            return all.conditions().stream().flatMap(c -> walk(c).stream()).toList();
        }
        if (condition instanceof AnyOfCondition any) {
            return any.conditions().stream().flatMap(c -> walk(c).stream()).toList();
        }
        if (condition instanceof NotCondition not) {
            // A negated age_group states which ages are excluded, not which are allowed, so it does not
            // count as naming the quest's intended ages — but it is still worth surfacing what it mentions.
            return walk(not.condition());
        }
        return List.of();
    }
}
