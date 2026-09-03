package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.compat.FtbqIds;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Satisfied once the linked FTB Quests quest is completed by the player's team (spec §18). Registered
 * unconditionally — this class has zero FTB imports; all real work happens behind
 * {@link FtbqBridge#Holder}, exactly like {@code FtbqQuestCompletedCondition}, so it parses and polls
 * identically whether or not FTB Quests is installed. {@code ObjectiveValidator} is what keeps a quest
 * using this objective out of the offer pool when FTB Quests is actually absent (an unsatisfiable
 * objective must never be offered).
 *
 * <pre>{ "type": "mcaquests:ftbq_complete_quest", "quest": "1A2B3C4D5E6F7081",
 *   "already_complete": "satisfy", "display_name": {"text": "the Ancient Tome chapter"} }</pre>
 *
 * <p><b>{@code already_complete}</b> ({@link AlreadyCompleteMode}, default {@code satisfy}): {@code satisfy}
 * means an FTB quest that was already done before this MCA quest was accepted satisfies the objective on
 * the very first poll; {@code block_offer} additionally desugars into {@code QuestDefinition}'s
 * {@code effectiveConditions()} — see that method — wrapping the quest's offer gate with
 * {@code not(ftbq_quest_completed)} so the offer never appears once the linked FTB quest is done.
 *
 * <p><b>Latching:</b> once {@link #poll} sets progress to 1 it is never un-set, even if the FTB quest's
 * own completion state later resets (e.g. an admin resets it, or the integration disappears mid-save) —
 * matching every other sticky poll-driven objective in this codebase (cf. {@code ReachLocationObjective}).
 */
public record FtbqCompleteQuestObjective(String quest, AlreadyCompleteMode alreadyComplete,
                                          Optional<QuestText> displayName) implements PollingObjective {

    private static final String TYPE_ID = "mcaquests:ftbq_complete_quest";

    public static final MapCodec<FtbqCompleteQuestObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FtbqIds.hexIdCodec(TYPE_ID, "quest").fieldOf("quest").forGetter(FtbqCompleteQuestObjective::quest),
            AlreadyCompleteMode.CODEC.optionalFieldOf("already_complete", AlreadyCompleteMode.SATISFY)
                    .forGetter(FtbqCompleteQuestObjective::alreadyComplete),
            QuestText.CODEC.optionalFieldOf("display_name").forGetter(FtbqCompleteQuestObjective::displayName)
    ).apply(instance, FtbqCompleteQuestObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.FTBQ_COMPLETE_QUEST;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.ftbq_complete_quest", displayOrFallback());
    }

    /** {@code display_name} resolved, or the documented fallback naming the raw hex id (spec §18). */
    private Component displayOrFallback() {
        return displayName.map(QuestText::resolve)
                .orElseGet(() -> Component.literal("the linked FTB quest (" + quest + ")"));
    }

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return false;
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress) {
        if (progress.count() >= 1) {
            return false; // latched: never re-checked, let alone un-satisfied, once complete
        }
        try {
            FtbqBridge bridge = FtbqBridge.Holder.get();
            if (bridge.isAvailable() && bridge.questIdExists(quest) && bridge.isQuestCompleted(player, quest)) {
                progress.setCount(1);
                return true;
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] {} poll failed for id '{}'", TYPE_ID, quest, t);
        }
        return false;
    }
}
