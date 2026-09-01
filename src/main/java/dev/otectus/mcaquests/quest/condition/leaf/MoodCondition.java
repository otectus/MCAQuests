package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Offered only when the giver's MCA mood value is within an optional {@code min}/{@code max} range
 * and/or its mood name is one of {@code moods} (v0.3.0). At least one field must be present. Mood
 * names are data-driven in MCA, so they are not validated against a closed set.
 */
public record MoodCondition(Optional<Integer> min, Optional<Integer> max, Optional<List<String>> moods)
        implements QuestCondition {

    // mapCodec(...)...codec(), never create(...) chained: a Codec that is not a MapCodecCodec
    // makes DFU's dispatch look for the fields under a nested "value" key instead of inline
    // beside "type", and optionalFieldOf then swallows the mismatch silently.
    // See DispatchedCodecInlinesTest.
    public static final Codec<MoodCondition> CODEC = RecordCodecBuilder.<MoodCondition>mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("min").forGetter(MoodCondition::min),
            Codec.INT.optionalFieldOf("max").forGetter(MoodCondition::max),
            McaConditionCodecs.lowercaseNonEmptyList("mood").optionalFieldOf("moods").forGetter(MoodCondition::moods)
    ).apply(instance, MoodCondition::new)).flatXmap(MoodCondition::validate, MoodCondition::validate).codec();

    private static DataResult<MoodCondition> validate(MoodCondition condition) {
        if (condition.min.isEmpty() && condition.max.isEmpty() && condition.moods.isEmpty()) {
            return DataResult.error(() -> "mcaquests:mood needs at least one of 'min', 'max', or 'moods'");
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.MOOD;
    }

    @Override
    public boolean test(QuestContext context) {
        if (min.isPresent() || max.isPresent()) {
            OptionalInt value = context.mca().moodValue();
            if (value.isEmpty()) {
                return false;
            }
            int v = value.getAsInt();
            if (!(min.map(m -> v >= m).orElse(true) && max.map(m -> v <= m).orElse(true))) {
                return false;
            }
        }
        if (moods.isPresent()) {
            Optional<String> name = context.mca().moodName();
            return name.isPresent() && moods.get().contains(name.get());
        }
        return true;
    }
}
