package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Tests the character a village has acquired from what its people have built (Townstead spec §5.1).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_spirit",
 *   "spirit": "industrious",
 *   "minimum_points": 60,
 *   "minimum_tier": 2,
 *   "classification": "single"
 * }
 * }</pre>
 *
 * <p>Every field is optional and all present ones must hold, so this reads as "a village that is at
 * least this". With no fields at all it is simply "this village has a spirit reading", which is the
 * honest gate for content that only needs Townstead's spirit system to be working at all.
 *
 * <p>{@code minimum_points} and {@code minimum_share} are per-spirit when {@code spirit} names one and
 * village-wide when it does not, so "sixty points of anything" and "sixty points of industrious" are
 * both sayable without a second field to tell them apart.
 */
public record TownsteadSpiritCondition(Optional<String> spirit, OptionalInt minimumPoints,
                                       OptionalInt minimumTier, Optional<String> classification,
                                       Optional<String> primary,
                                       Optional<Double> minimumShare) implements QuestCondition {

    public static final MapCodec<TownsteadSpiritCondition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritCondition::spirit),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_points")
                            .forGetter(condition -> box(condition.minimumPoints)),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_tier")
                            .forGetter(condition -> box(condition.minimumTier)),
                    StrictCodecs.strictOptional(Codec.STRING, "classification")
                            .forGetter(TownsteadSpiritCondition::classification),
                    StrictCodecs.strictOptional(Codec.STRING, "primary")
                            .forGetter(TownsteadSpiritCondition::primary),
                    StrictCodecs.strictOptional(Codec.DOUBLE, "minimum_share")
                            .forGetter(TownsteadSpiritCondition::minimumShare)
            ).apply(instance, (spirit, points, tier, classification, primary, share) ->
                    new TownsteadSpiritCondition(spirit, unbox(points), unbox(tier), classification,
                            primary, share)));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_SPIRIT;
    }

    @Override
    public boolean test(QuestContext context) {
        OptionalInt village = McaCompat.getHomeVillageId(context.villager());
        if (village.isEmpty()) {
            return false;
        }
        Optional<TownsteadSpiritView> reading =
                context.mca().townstead().spirit(context.level(), village.getAsInt());
        if (reading.isEmpty()) {
            return false;
        }
        TownsteadSpiritView view = reading.get();
        if (minimumTier.isPresent() && view.tier() < minimumTier.getAsInt()) {
            return false;
        }
        if (classification.isPresent() && !equalsIgnoreCase(classification.get(), view.classification())) {
            return false;
        }
        if (primary.isPresent() && !equalsIgnoreCase(primary.get(), view.primaryId())) {
            return false;
        }
        if (minimumPoints.isPresent()) {
            int points = spirit.map(view::pointsFor).orElseGet(view::total);
            if (points < minimumPoints.getAsInt()) {
                return false;
            }
        }
        return minimumShare.isEmpty() || spirit.map(view::shareOf).orElse(1.0D) >= minimumShare.get();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    @Override
    public Component describe() {
        // A spirit id and a classification are different vocabularies -- "nautical" and "blend" are
        // not the same kind of word -- so they get different sentences rather than one with whichever
        // happened to be set dropped into it.
        if (spirit.isPresent()) {
            return Component.translatable("mcaquests.condition.townstead_spirit",
                    dev.otectus.mcaquests.quest.TownsteadNames.spirit(spirit.get()));
        }
        if (classification.isPresent()) {
            return Component.translatable("mcaquests.condition.townstead_classification",
                    Component.translatableWithFallback(
                            "mcaquests.townstead.classification." + classification.get(),
                            classification.get()));
        }
        return Component.translatable("mcaquests.condition.townstead_spirit_any");
    }
}
