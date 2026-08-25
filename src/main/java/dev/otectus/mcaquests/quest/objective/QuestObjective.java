package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A single requirement a player must satisfy to complete a quest (spec section 14). Registry-driven
 * via {@link ObjectiveTypes} so add-ons can register new types.
 *
 * <p>Two flavours: <em>possession</em> objectives (e.g. {@code item_delivery}) compute completion
 * from the player's live state; <em>accumulation</em> objectives (kill, break — Phase 2) advance a
 * counter in {@link ObjectiveProgress} via Forge events. {@link #isEventDriven()} distinguishes them.
 */
public interface QuestObjective {

    QuestObjectiveType<?> type();

    /** Human-readable one-line summary for the quest card, e.g. "Deliver 24 Wheat". */
    Component describe();

    /**
     * Context-aware variant used to build the active quest-log line: lets an objective resolve a concrete
     * target villager's real name (and a location hint) server-side, so the player knows who to find.
     * Defaults to {@link #describe()}.
     */
    default Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return describe();
    }

    /**
     * As {@link #describe(ServerPlayer, ActiveQuest, ServerLevel)}, with the objective's own
     * {@link ObjectiveProgress} in hand so a villager-targeted objective can name the villager it has
     * actually <em>bound</em> rather than re-resolving one. Defaults to the progress-free variant, so
     * objective types that do not target a villager need not implement it.
     */
    default Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                               ServerLevel level) {
        return describe(player, active, level);
    }

    /** Target amount (for progress display). */
    int required();

    /** Current amount toward {@link #required()} for this player/progress (clamped to required). */
    int current(ServerPlayer player, ObjectiveProgress progress);

    /** True when this objective is fully satisfied. */
    boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress);

    /** Apply any consumption/side effects at turn-in, after every objective has been validated. */
    default void consumeOnTurnIn(ServerPlayer player, ObjectiveProgress progress) {
    }

    /** True if progress accumulates via game events rather than a live state check. */
    default boolean isEventDriven() {
        return false;
    }

    /**
     * True when this objective would <em>already</em> be satisfied for this player and giver at the moment
     * the quest is about to be offered — in which case offering it is nonsense, because the player could
     * accept and turn it straight back in for the full reward without doing anything.
     *
     * <p>Checked by {@code QuestManager.eligibleOffers} against every objective of every otherwise-eligible
     * quest; one {@code true} drops the quest from the offer pool for as long as the condition holds.
     * Implementations must be cheap and side-effect free, and must answer {@code false} when they cannot
     * tell (an unloaded target, an anchor that will not resolve) — an offer that might be doable is always
     * better than one silently withheld.
     *
     * <p>Defaults to {@code false}, so existing objective types — including those registered by add-ons —
     * are unaffected and keep compiling.
     */
    default boolean isTriviallySatisfied(QuestContext context) {
        return false;
    }

    /**
     * Datapack-load validation hook (spec section 26). Appends semantic problems a codec cannot catch
     * (e.g. a target referencing a field its mode requires, an out-of-range radius). Messages should
     * be prefixed with {@code "Quest '<questId>': objective[<index>] "}. No-op by default, so existing
     * objective types are unaffected. Runs against the literal authored objective, so for template
     * quests it sees pre-substitution values (template semantics remain the TemplateValidator's job).
     */
    default void validate(ResourceLocation questId, int index, List<String> errors) {
    }
}
