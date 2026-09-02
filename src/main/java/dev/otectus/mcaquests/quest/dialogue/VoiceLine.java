package dev.otectus.mcaquests.quest.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * One thing a villager might say, and the circumstances in which they would say it.
 *
 * <p>{@code when} is the <b>existing</b> {@link QuestCondition} language — the same one quests are
 * gated with. That is the whole design of this feature: personality, mood, time of day, weather,
 * hearts, reputation tier, relationship state, age group and every condition added in future become
 * available to dialogue for free, and the mod gains no second mini-language to document, validate or
 * teach. A line with no {@code when} is a fallback and always applies.
 *
 * @param weight relative likelihood among the lines that match; a heavier line is chosen more often
 */
public record VoiceLine(Optional<QuestCondition> when, QuestText text, int weight) {

    public static final Codec<VoiceLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ConditionTypes.CODEC.optionalFieldOf("when").forGetter(VoiceLine::when),
            QuestText.MAP_CODEC.forGetter(VoiceLine::text),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(VoiceLine::weight)
    ).apply(instance, VoiceLine::new));

    /** Whether this line is one the villager would say right now. */
    public boolean matches(QuestContext context) {
        return when.map(condition -> condition.test(context)).orElse(true);
    }

    /** A line with no {@code when} is the pool's fallback: always eligible, never preferred. */
    public boolean isFallback() {
        return when.isEmpty();
    }
}
