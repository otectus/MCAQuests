package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/** Registry entry for a {@link SituationTrigger} type. */
public record SituationTriggerType<T extends SituationTrigger>(ResourceLocation id, MapCodec<T> codec) {
}
