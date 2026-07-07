package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-controlled offer shaping for a quest (spec section 18): an optional {@code priority} tier plus a
 * list of conditional {@code weight_bonus} entries. Both keys live at the quest's top level — this record
 * only groups them so {@link QuestDefinition}'s codec stays within DataFixerUpper's 16-field {@code group}
 * limit. The default ({@link #NONE}) is no explicit priority and no bonuses — identical to the pre-existing
 * behaviour, so standalone quests are unaffected.
 */
public record OfferShaping(Optional<Integer> priority, List<WeightBonus> weightBonus) {

    public static final OfferShaping NONE = new OfferShaping(Optional.empty(), List.of());

    public static final MapCodec<OfferShaping> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("priority").forGetter(OfferShaping::priority),
            WeightBonus.CODEC.listOf().optionalFieldOf("weight_bonus", List.of()).forGetter(OfferShaping::weightBonus)
    ).apply(instance, OfferShaping::new));
}
