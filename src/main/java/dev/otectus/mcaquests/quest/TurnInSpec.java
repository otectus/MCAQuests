package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * How a quest may be turned in (spec section 17): {@code {"turn_in": {"mode": "...", "professions": [...]}}}.
 * {@code professions} only applies to {@link TurnInMode#SPECIFIED_PROFESSION}.
 */
public record TurnInSpec(TurnInMode mode, List<ResourceLocation> professions) {

    public static final TurnInSpec DEFAULT = new TurnInSpec(TurnInMode.ORIGINAL_GIVER, List.of());

    public static final Codec<TurnInSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TurnInMode.CODEC.lenientOptionalFieldOf("mode", TurnInMode.ORIGINAL_GIVER).forGetter(TurnInSpec::mode),
            ResourceLocation.CODEC.listOf().lenientOptionalFieldOf("professions", List.of()).forGetter(TurnInSpec::professions)
    ).apply(instance, TurnInSpec::new));
}
