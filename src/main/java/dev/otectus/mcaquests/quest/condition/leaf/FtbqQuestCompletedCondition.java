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
 * Reads whether an FTB Quests quest has been completed by the player's team (spec §17). Registered
 * unconditionally — this class has zero FTB imports; all real work happens behind
 * {@link FtbqBridge#Holder}, so the condition parses and evaluates identically whether or not FTB
 * Quests is installed.
 *
 * <pre>{ "type": "mcaquests:ftbq_quest_completed", "quest": "1A2B3C4D5E6F7081", "when_missing": "not_met" }</pre>
 *
 * <p><b>{@code when_missing} semantics</b> (spec §17): this is the result whenever FTB Quests' real
 * completion state cannot be authoritatively consulted — the integration is absent/disabled, the
 * bridge itself fails, <em>or the id doesn't resolve</em> to a quest in the loaded book. That last
 * case matters because {@link FtbqBridge#isQuestCompleted} alone cannot distinguish "quest exists and
 * is incomplete" from "no such quest" — both return {@code false}. So this condition first checks
 * {@link FtbqBridge#questIdExists} to disambiguate: only "available AND exists" consults
 * {@code isQuestCompleted} for the real answer; every other case (unavailable, doesn't exist, or any
 * thrown exception) falls back to {@code when_missing}. This lets authors write both "FTB-gated bonus
 * quest" ({@code not_met}: vanishes without FTBQ or with a bad id) and "catch-up quest hidden once the
 * FTB quest is done" ({@code met}, combined with {@code not}).
 */
public record FtbqQuestCompletedCondition(String quest, FtbqWhenMissing whenMissing) implements QuestCondition {

    private static final String TYPE_ID = "mcaquests:ftbq_quest_completed";

    public static final Codec<FtbqQuestCompletedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FtbqIds.hexIdCodec(TYPE_ID, "quest").fieldOf("quest").forGetter(FtbqQuestCompletedCondition::quest),
            FtbqWhenMissing.CODEC.optionalFieldOf("when_missing", FtbqWhenMissing.NOT_MET)
                    .forGetter(FtbqQuestCompletedCondition::whenMissing)
    ).apply(instance, FtbqQuestCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.FTBQ_QUEST_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        try {
            FtbqBridge bridge = FtbqBridge.Holder.get();
            if (bridge.isAvailable() && bridge.questIdExists(quest)) {
                return bridge.isQuestCompleted(context.player(), quest);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] {} evaluation failed for id '{}'; falling back to when_missing",
                    TYPE_ID, quest, t);
        }
        return whenMissing.asBoolean();
    }
}
