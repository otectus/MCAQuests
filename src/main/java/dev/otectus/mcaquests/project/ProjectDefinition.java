package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Optional;

/**
 * An immutable, data-loaded community project (spec 0.4.0). Parsed from
 * {@code data/<ns>/mcaquests/projects/**.json}. A project is a shared, multi-stage quest whose
 * progress lives in world storage rather than on any one player, so multiple players and villagers can
 * work toward it. It deliberately reuses the existing reward/condition systems and a parallel shared
 * objective system; only the container, storage, and lifecycle are new.
 */
public record ProjectDefinition(
        ResourceLocation id,
        boolean enabled,
        int weight,
        Optional<QuestText> title,
        Optional<String> category,
        ProjectScopeSpec scope,
        SponsorSpec sponsor,
        List<ProjectPhase> phases,
        Optional<QuestCondition> conditions,
        ReputationSpec reputation,
        Optional<ResourceLocation> followUp,
        Optional<FailureSpec> failure) {

    public static final Codec<ProjectDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ProjectDefinition::id),
            Codec.BOOL.lenientOptionalFieldOf("enabled", true).forGetter(ProjectDefinition::enabled),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("weight", 1).forGetter(ProjectDefinition::weight),
            QuestText.CODEC.lenientOptionalFieldOf("title").forGetter(ProjectDefinition::title),
            Codec.STRING.lenientOptionalFieldOf("category").forGetter(ProjectDefinition::category),
            ProjectScopeSpec.CODEC.fieldOf("scope").forGetter(ProjectDefinition::scope),
            SponsorSpec.CODEC.lenientOptionalFieldOf("sponsor", SponsorSpec.ANY).forGetter(ProjectDefinition::sponsor),
            ProjectPhase.CODEC.listOf().lenientOptionalFieldOf("phases", List.of()).forGetter(ProjectDefinition::phases),
            ConditionTypes.CODEC.lenientOptionalFieldOf("conditions").forGetter(ProjectDefinition::conditions),
            ReputationSpec.CODEC.lenientOptionalFieldOf("reputation", ReputationSpec.NONE).forGetter(ProjectDefinition::reputation),
            ResourceLocation.CODEC.lenientOptionalFieldOf("follow_up").forGetter(ProjectDefinition::followUp),
            FailureSpec.CODEC.lenientOptionalFieldOf("failure").forGetter(ProjectDefinition::failure)
    ).apply(instance, ProjectDefinition::new));

    /** Translation key for this project's display title, e.g. {@code mcaquests.project.<path>.title}. */
    public String titleKey() {
        return "mcaquests.project." + id.getPath() + ".title";
    }

    public Component displayTitle() {
        return title.map(QuestText::resolve).orElseGet(() -> Component.translatable(titleKey()));
    }

    public int phaseCount() {
        return phases.size();
    }

    public ProjectPhase phase(int index) {
        return phases.get(index);
    }

    public ProjectScope scopeType() {
        return scope.scope();
    }
}
