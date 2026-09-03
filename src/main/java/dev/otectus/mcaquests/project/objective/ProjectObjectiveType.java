package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/** Registry entry for a project objective type: its id and the {@link MapCodec} for its data. */
public record ProjectObjectiveType<T extends ProjectObjective>(ResourceLocation id, MapCodec<T> codec) {
}
