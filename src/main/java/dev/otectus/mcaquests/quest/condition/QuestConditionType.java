package dev.otectus.mcaquests.quest.condition;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/** Registry entry for a leaf condition type. */
public record QuestConditionType<T extends QuestCondition>(ResourceLocation id, Codec<T> codec) {
}
