package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.compat.FtbqIds;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Reads whether an FTB Quests chapter has been completed by the player's team (spec §17). Registered
 * unconditionally — zero FTB imports; all real work happens behind {@link FtbqBridge#Holder}.
 *
 * <pre>{ "type": "mcaquests:ftbq_chapter_completed", "chapter": "0123456789ABCDEF", "when_missing": "not_met" }</pre>
 *
 * <p>{@code when_missing} semantics mirror {@link FtbqQuestCompletedCondition}: it is the fallback
 * whenever FTB Quests' state cannot be authoritatively consulted — absent/disabled integration, a
 * thrown bridge failure, or an id that doesn't resolve to a chapter in the loaded book. Because
 * {@link FtbqBridge#isChapterCompleted} alone cannot distinguish "chapter exists, incomplete" from
 * "no such chapter" (both {@code false}), {@link FtbqBridge#chapterIdExists} disambiguates first: only
 * "available AND exists" consults the real completion state.
 */
public record FtbqChapterCompletedCondition(String chapter, FtbqWhenMissing whenMissing) implements QuestCondition {

    private static final String TYPE_ID = "mcaquests:ftbq_chapter_completed";

    public static final Codec<FtbqChapterCompletedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FtbqIds.hexIdCodec(TYPE_ID, "chapter").fieldOf("chapter").forGetter(FtbqChapterCompletedCondition::chapter),
            FtbqWhenMissing.CODEC.optionalFieldOf("when_missing", FtbqWhenMissing.NOT_MET)
                    .forGetter(FtbqChapterCompletedCondition::whenMissing)
    ).apply(instance, FtbqChapterCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.FTBQ_CHAPTER_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        try {
            FtbqBridge bridge = FtbqBridge.Holder.get();
            if (bridge.isAvailable() && bridge.chapterIdExists(chapter)) {
                return bridge.isChapterCompleted(context.player(), chapter);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] {} evaluation failed for id '{}'; falling back to when_missing",
                    TYPE_ID, chapter, t);
        }
        return whenMissing.asBoolean();
    }
}
