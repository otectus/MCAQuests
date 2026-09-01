package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Locale;

/**
 * Offered only when the giver is the given {@code relation} to the interacting player in MCA's
 * family tree (v0.3.0). {@code relation} defaults to {@code any}.
 *
 * <p>The field is read as a lenient string and validated on the whole record, rather than via
 * {@code optionalFieldOf(name, default)} — the latter silently swallows a decode error and
 * substitutes the default, which would let an explicitly invalid value through.
 */
public record IsFamilyMemberCondition(String relation) implements QuestCondition {

    // mapCodec(...)...codec(), never create(...) chained: a Codec that is not a MapCodecCodec
    // makes DFU's dispatch look for the fields under a nested "value" key instead of inline
    // beside "type", and optionalFieldOf then swallows the mismatch silently.
    // See DispatchedCodecInlinesTest.
    public static final Codec<IsFamilyMemberCondition> CODEC = RecordCodecBuilder.<IsFamilyMemberCondition>mapCodec(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("relation", "any").forGetter(IsFamilyMemberCondition::relation)
            ).apply(instance, IsFamilyMemberCondition::new)).flatXmap(IsFamilyMemberCondition::validate, IsFamilyMemberCondition::validate).codec();

    private static DataResult<IsFamilyMemberCondition> validate(IsFamilyMemberCondition condition) {
        String value = condition.relation.toLowerCase(Locale.ROOT);
        if (!McaConditionCodecs.FAMILY_RELATIONS.contains(value)) {
            return DataResult.error(() -> "Unknown family relation: '" + condition.relation
                    + "' (expected one of " + McaConditionCodecs.FAMILY_RELATIONS + ')');
        }
        return DataResult.success(value.equals(condition.relation) ? condition : new IsFamilyMemberCondition(value));
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.IS_FAMILY_MEMBER;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().isFamilyOfPlayer(relation);
    }
}
