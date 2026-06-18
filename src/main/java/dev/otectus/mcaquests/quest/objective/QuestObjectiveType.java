package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry entry for an objective type: its id and the {@link Codec} that (de)serialises its data.
 * The codec is built with {@code RecordCodecBuilder.create(...)} so it inlines cleanly under the
 * dispatch codec in {@link ObjectiveTypes}.
 */
public record QuestObjectiveType<T extends QuestObjective>(ResourceLocation id, Codec<T> codec) {
}
