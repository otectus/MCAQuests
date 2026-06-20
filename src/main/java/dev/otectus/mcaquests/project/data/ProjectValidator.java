package dev.otectus.mcaquests.project.data;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.data.GraphCycles;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectPhase;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.SharedReward;
import dev.otectus.mcaquests.project.SharedRewardTarget;
import dev.otectus.mcaquests.quest.reward.CommandReward;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-project semantic validation, run after all projects parse (spec 0.4.0). The dispatch codecs
 * already reject unknown scope/objective/reward type ids and malformed numbers; this adds the checks a
 * codec cannot see. Hard problems are reported as {@code Project '<id>': <reason>.}; advisory findings
 * are prefixed {@code Warning — } so they show in {@code /mcaquests project validate} without tripping
 * strict mode.
 */
public final class ProjectValidator {

    public static final String WARNING_PREFIX = "Warning — ";

    private ProjectValidator() {
    }

    public static boolean isWarning(String message) {
        return message.startsWith(WARNING_PREFIX);
    }

    public static void validate(Map<ResourceLocation, ProjectDefinition> loaded, List<String> errors) {
        boolean mcaLoaded = ModList.get() != null && ModList.get().isLoaded("mca");
        boolean allowCommands = McaQuestsConfig.COMMON.allowProjectCommandRewards.get();

        for (ProjectDefinition def : loaded.values()) {
            ResourceLocation id = def.id();

            if (def.phases().isEmpty()) {
                errors.add(error(id, "has no phases; a project needs at least one phase"));
                continue;
            }

            if (def.scopeType().requiresMcaData() && !mcaLoaded) {
                errors.add(warn(id, "scope " + def.scopeType().lower()
                        + " depends on MCA village/family data, which is not available here; it will never become available"));
            }

            if (def.scopeType() == ProjectScope.PROFESSION && def.scope().professions().isEmpty()
                    && def.sponsor().professions().isEmpty()) {
                errors.add(warn(id, "profession scope with no scope.professions and no sponsor.professions; "
                        + "every profession in the village will share one pool"));
            }

            // follow-up must resolve.
            def.followUp().ifPresent(target -> {
                ProjectDefinition t = loaded.get(target);
                if (t == null) {
                    errors.add(error(id, "follow_up references unknown project '" + target + "'"));
                } else if (!t.enabled()) {
                    errors.add(error(id, "follow_up references disabled project '" + target + "'"));
                }
            });

            for (int i = 0; i < def.phases().size(); i++) {
                validatePhase(def, i, errors, allowCommands);
            }
        }

        // Circular follow-up across projects.
        GraphCycles.findCycle(buildFollowUpGraph(loaded)).ifPresent(cycle ->
                errors.add("Projects contain a circular follow_up reference: " + describeCycle(cycle)));
    }

    private static void validatePhase(ProjectDefinition def, int index, List<String> errors, boolean allowCommands) {
        ResourceLocation id = def.id();
        ProjectPhase phase = def.phase(index);
        String key = phase.keyOr(index);
        boolean lastPhase = index == def.phases().size() - 1;

        if (phase.objectives().isEmpty() && !lastPhase) {
            errors.add(warn(id, "phase '" + key + "' has no objectives, so it completes instantly and is skipped"));
        }

        for (SharedReward reward : phase.rewards()) {
            if (!allowCommands && reward.reward() instanceof CommandReward) {
                errors.add(warn(id, "phase '" + key
                        + "' uses a command reward but allowProjectCommandRewards is disabled; it will be skipped at grant"));
            }
            if (reward.target() == SharedRewardTarget.SPONSOR_VILLAGE
                    && (def.scopeType() == ProjectScope.PLAYER || def.scopeType() == ProjectScope.VILLAGER)) {
                errors.add(warn(id, "phase '" + key + "' reward target 'sponsor_village' has no village in "
                        + def.scopeType().lower() + " scope"));
            }
            if ((reward.target() == SharedRewardTarget.TOP_CONTRIBUTOR
                    || reward.target() == SharedRewardTarget.CONTRIBUTORS)
                    && phase.objectives().isEmpty()) {
                errors.add(warn(id, "phase '" + key + "' reward target '" + reward.target().name().toLowerCase()
                        + "' but the phase has no objectives, so there are no contributors"));
            }
        }
    }

    private static Map<ResourceLocation, List<ResourceLocation>> buildFollowUpGraph(
            Map<ResourceLocation, ProjectDefinition> loaded) {
        Map<ResourceLocation, List<ResourceLocation>> graph = new LinkedHashMap<>();
        for (ProjectDefinition def : loaded.values()) {
            graph.put(def.id(), def.followUp().filter(loaded::containsKey).map(List::of).orElse(List.of()));
        }
        return graph;
    }

    private static String describeCycle(List<ResourceLocation> cycle) {
        return String.join(" -> ", cycle.stream().map(ResourceLocation::toString).toList());
    }

    private static String error(ResourceLocation id, String reason) {
        return "Project '" + id + "': " + reason + ".";
    }

    private static String warn(ResourceLocation id, String reason) {
        return WARNING_PREFIX + "Project '" + id + "': " + reason + ".";
    }
}
