package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/** Registry entry for a project objective type: its id and the {@link Codec} for its data. */
public record ProjectObjectiveType<T extends ProjectObjective>(ResourceLocation id, Codec<T> codec) {
}
