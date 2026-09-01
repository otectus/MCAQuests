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
 *
 * <p>{@code offer_group} (1.4.1) is the newest member and joined this record for the same reason the
 * others did: {@code QuestDefinition}'s own {@code RecordCodecBuilder} group is full. It names a
 * <em>kind</em> of quest — a need, a shift, a season, an adventure — so that a menu with three slots
 * and two hundred eligible quests does not fill all three with variations of "someone is hungry". See
 * {@code QuestManager}'s selection pass for how it is honoured, and note that a quest without one keeps
 * exactly its old behaviour: ungrouped quests compete on weight as they always have.
 */
public record OfferShaping(Optional<Integer> priority, List<WeightBonus> weightBonus,
                           Optional<QuestDifficulty> difficulty, Optional<String> offerGroup) {

    public static final OfferShaping NONE =
            new OfferShaping(Optional.empty(), List.of(), Optional.empty(), Optional.empty());

    public static final MapCodec<OfferShaping> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("priority").forGetter(OfferShaping::priority),
            WeightBonus.CODEC.listOf().optionalFieldOf("weight_bonus", List.of()).forGetter(OfferShaping::weightBonus),
            QuestDifficulty.CODEC.optionalFieldOf("difficulty").forGetter(OfferShaping::difficulty),
            Codec.STRING.optionalFieldOf("offer_group").forGetter(OfferShaping::offerGroup)
    ).apply(instance, OfferShaping::new));

    /** The pre-1.4.1 shape, for callers and tests that predate {@code offer_group}. */
    public OfferShaping(Optional<Integer> priority, List<WeightBonus> weightBonus,
                        Optional<QuestDifficulty> difficulty) {
        this(priority, weightBonus, difficulty, Optional.empty());
    }
}
