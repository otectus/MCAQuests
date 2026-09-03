package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

/** Registry entry for a reward type: its id and (de)serialisation codec. */
public record QuestRewardType<T extends QuestReward>(ResourceLocation id, MapCodec<T> codec) {
}
