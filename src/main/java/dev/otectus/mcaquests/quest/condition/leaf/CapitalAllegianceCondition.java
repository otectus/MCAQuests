package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalsQueries;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * True when the player has sworn allegiance to a capital (MCA Capitals).
 *
 * <pre>{@code {"type": "mcaquests:capital_allegiance", "match": "giver"}}</pre>
 *
 * <p>{@code match} is {@code giver} by default, meaning the declared capital must be <em>this</em>
 * capital — the gate for content a court only offers its own sworn people. {@code any} asks only that
 * the player has declared for somebody, which is how a pack recognises a player who has taken a side
 * without caring which.
 */
public record CapitalAllegianceCondition(Match match, boolean present) implements QuestCondition {

    /** Which declaration counts. */
    public enum Match {
        GIVER,
        ANY;

        public static final Codec<Match> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown capital allegiance match: '" + name
                                + "' (expected giver/any)");
                    }
                },
                match -> DataResult.success(match.name().toLowerCase(Locale.ROOT)));
    }

    public static final MapCodec<CapitalAllegianceCondition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Match.CODEC, "match", Match.GIVER)
                            .forGetter(CapitalAllegianceCondition::match),
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CapitalAllegianceCondition::present)
            ).apply(instance, CapitalAllegianceCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CAPITAL_ALLEGIANCE;
    }

    @Override
    public boolean test(QuestContext context) {
        if (!CapitalsCompat.bridge().has(CapitalsCapability.ALLEGIANCE)
                || match == Match.GIVER && !CapitalsCompat.bridge().has(CapitalsCapability.REGISTRY)
                || context.player() == null) {
            return false;
        }
        Optional<UUID> declared = CapitalsCompat.bridge()
                .declaredAllegiance(context.level(), context.player().getUUID());
        boolean sworn = declared.isPresent() && (match == Match.ANY
                || CapitalsQueries.giverCapital(context.villager())
                        .map(cap -> cap.capitalId().equals(declared.get()))
                        .orElse(false));
        return CapitalsCompat.bridge().has(CapitalsCapability.ALLEGIANCE)
                && (match == Match.ANY || CapitalsCompat.bridge().has(CapitalsCapability.REGISTRY))
                && sworn == present;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.capital_allegiance");
    }
}
