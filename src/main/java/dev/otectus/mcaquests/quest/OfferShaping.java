package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Top-level quest keys that {@link QuestDefinition}'s own codec has no room for: an optional
 * {@code priority} tier, a list of conditional {@code weight_bonus} entries (spec section 18), and an
 * optional {@code difficulty} band (spec 1.1.0). Every key lives at the quest's top level — this record
 * only groups them so {@link QuestDefinition}'s codec stays within DataFixerUpper's 16-field {@code group}
 * limit, and {@code QuestDefinition} re-exposes each one through its own accessor. The default
 * ({@link #NONE}) is no explicit priority, no bonuses, and no declared difficulty — identical to the
 * pre-existing behaviour, so standalone quests are unaffected.
 */
public record OfferShaping(Optional<Integer> priority, List<WeightBonus> weightBonus,
                           Optional<QuestDifficulty> difficulty) {

    public static final OfferShaping NONE = new OfferShaping(Optional.empty(), List.of(), Optional.empty());

    public static final MapCodec<OfferShaping> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("priority").forGetter(OfferShaping::priority),
            WeightBonus.CODEC.listOf().lenientOptionalFieldOf("weight_bonus", List.of()).forGetter(OfferShaping::weightBonus),
            QuestDifficulty.CODEC.lenientOptionalFieldOf("difficulty").forGetter(OfferShaping::difficulty)
    ).apply(instance, OfferShaping::new));
}
