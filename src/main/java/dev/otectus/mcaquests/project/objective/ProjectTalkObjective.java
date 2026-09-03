package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import javax.annotation.Nullable;

/**
 * Talk to (interact with) distinct MCA villagers of a given profession for the project (spec 0.4.0).
 * Event-driven; {@code ProjectProgressEvents} dedupes by villager UUID via the shared progress's
 * talked-to set, so each villager counts once.
 */
public record ProjectTalkObjective(ResourceLocation profession, int count) implements ProjectObjective {

    public static final MapCodec<ProjectTalkObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profession").forGetter(ProjectTalkObjective::profession),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ProjectTalkObjective::count)
    ).apply(instance, ProjectTalkObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.PROJECT_TALK_TO_PROFESSION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.project_talk_to_profession", count,
                DisplayNames.name(profession));
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * Profession match under the configured {@code professionMatchingMode}, mirroring the regular
     * {@code talk_to_profession} objective so both honour STRICT / NORMALIZED / LOOSE identically.
     */
    public boolean matches(@Nullable ResourceLocation talkedToProfession) {
        return ProfessionMatcher.matches(profession, talkedToProfession);
    }
}
