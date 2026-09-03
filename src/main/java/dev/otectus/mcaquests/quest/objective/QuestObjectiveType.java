package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry entry for an objective type: its id and the {@link MapCodec} that (de)serialises its data.
 * The codec is built with {@code RecordCodecBuilder.mapCodec(...)} so it inlines cleanly under the
 * dispatch codec in {@link ObjectiveTypes}.
 */
public record QuestObjectiveType<T extends QuestObjective>(ResourceLocation id, MapCodec<T> codec) {
}
