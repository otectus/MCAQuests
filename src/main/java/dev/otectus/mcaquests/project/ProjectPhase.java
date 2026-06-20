package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjectiveTypes;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One stage of a {@link ProjectDefinition}. Phases run in order: a phase is only entered once every
 * earlier phase is complete (and its optional {@code unlock} condition passes). Reuses the existing
 * objective/reward/condition systems — only the container and the shared progress model are new.
 *
 * <ul>
 *   <li>{@code key} — author label for dialogue/display/debug (e.g. {@code "gather_stone"}).</li>
 *   <li>{@code dialogue} — sponsor lines keyed by the {@code QuestDefinition} state names
 *       ({@code offer}/{@code in_progress}/{@code ready}/{@code complete}).</li>
 *   <li>{@code objectives} — shared {@link ProjectObjective}s for this phase.</li>
 *   <li>{@code rewards} — {@link SharedReward}s granted when the phase completes.</li>
 *   <li>{@code unlock} — an extra condition gating entry into this phase, beyond "prior phase done".</li>
 * </ul>
 */
public record ProjectPhase(Optional<String> key,
                           Map<String, QuestText> dialogue,
                           List<ProjectObjective> objectives,
                           List<SharedReward> rewards,
                           Optional<QuestCondition> unlock) {

    public static final Codec<ProjectPhase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("key").forGetter(ProjectPhase::key),
            Codec.unboundedMap(Codec.STRING, QuestText.CODEC).optionalFieldOf("dialogue", Map.of()).forGetter(ProjectPhase::dialogue),
            ProjectObjectiveTypes.CODEC.listOf().optionalFieldOf("objectives", List.of()).forGetter(ProjectPhase::objectives),
            SharedReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(ProjectPhase::rewards),
            ConditionTypes.CODEC.optionalFieldOf("unlock").forGetter(ProjectPhase::unlock)
    ).apply(instance, ProjectPhase::new));

    public String keyOr(int index) {
        return key.orElse("phase_" + index);
    }

    /** Resolves a dialogue line for {@code state}, or {@code fallback} if this phase omits it. */
    public Component dialogueOr(String state, @Nullable Component fallback) {
        QuestText line = dialogue.get(state);
        if (line == null) {
            return fallback == null ? Component.empty() : fallback;
        }
        return line.resolve();
    }
}
