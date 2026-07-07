package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Cross-cutting validation of quest objectives, run after all quests load (spec section 26). Codecs
 * already reject unknown item/block/entity ids and out-of-range counts at parse time; this drives
 * each objective's {@link QuestObjective#validate} hook for the semantic checks a codec cannot express
 * (a {@code villager}/{@code anchor}/{@code structure} target missing the field its mode requires, an
 * unknown family relation, …). Problems are appended to the same error list surfaced by
 * {@code /mcaquests validate} and honour {@code strictJsonValidation}.
 */
public final class ObjectiveValidator {

    private ObjectiveValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        for (QuestDefinition def : loaded.values()) {
            List<QuestObjective> objectives = def.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                objectives.get(i).validate(def.id(), i, errors);
            }
        }
    }
}
