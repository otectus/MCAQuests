package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.capitals.CapitalRef;
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

/**
 * True when the village a quest is about is the seat of an <em>active</em> capital (MCA Capitals).
 *
 * <pre>{@code {"type": "mcaquests:capital_present", "subject": "giver"}}</pre>
 *
 * <p>{@code subject} chooses whose village is asked about: {@code giver} (the default) is the quest
 * giver's home village, {@code player_village} the village the player is standing in. The two differ
 * for a quest offered by a traveller, and the giver's village is the one every court quest means.
 *
 * <p>The opening gate of every bundled Capitals quest, and the reason the rest of them can assume a
 * throne exists. Capitals with no capital at all, a capital still being founded, or no MCA Capitals
 * installed are all the same answer here: not present.
 */
public record CapitalPresentCondition(Subject subject, boolean present) implements QuestCondition {

    /** Whose village is asked about. */
    public enum Subject {
        GIVER,
        PLAYER_VILLAGE;

        public static final Codec<Subject> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown capital subject: '" + name
                                + "' (expected giver/player_village)");
                    }
                },
                subject -> DataResult.success(subject.name().toLowerCase(Locale.ROOT)));
    }

    public static final Codec<CapitalPresentCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Subject.CODEC, "subject", Subject.GIVER)
                            .forGetter(CapitalPresentCondition::subject),
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CapitalPresentCondition::present)
            ).apply(instance, CapitalPresentCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CAPITAL_PRESENT;
    }

    @Override
    public boolean test(QuestContext context) {
        Optional<CapitalRef> capital = subject == Subject.GIVER
                ? CapitalsQueries.giverCapital(context.villager())
                : CapitalsQueries.playerVillageCapital(context.player());
        boolean active = capital.map(CapitalsCompat.bridge()::isActive).orElse(false);
        return active == present;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.capital_present");
    }
}
