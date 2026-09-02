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
     * An item to draw beside this objective's line, or empty for an objective with nothing to show.
     *
     * <p>Purely cosmetic, and never consulted for progress: an objective that asks for wheat may show
     * wheat, but {@link #isSatisfied} remains the only thing that decides whether it is done.
     *
     * <p>Defaults to empty, so existing objective types — including those registered by add-ons — are
     * unaffected and keep compiling, exactly like {@link #isTriviallySatisfied} and
     * {@link #unofferableReason} before it.
     */
    default net.minecraft.world.item.ItemStack icon() {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

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
     * Why this objective cannot be <em>offered</em> right now, if it cannot — because the thing it names
     * does not exist, or cannot be found.
     *
     * <p>Three questions, deliberately kept apart. {@link #isTriviallySatisfied} asks "would this already
     * be done?" and stops a quest being handed back for the reward the second it is accepted.
     * {@link #unavailableReason} asks "can I read the state this is about right now?" and suspends an
     * <em>accepted</em> quest without failing it. This one asks "is there anybody, or anywhere, for this
     * to be about at all?" — and its answer decides whether the quest is ever shown.
     *
     * <p>It exists because there was no such check. A quest could be gated on "does this villager have a
     * sibling in this village?" and then hand its objective a completely different sibling, chosen with no
     * status filter at all; and because MCA leaves the dead on a village's resident roll forever, the gate
     * counted them. Players were asked to deliver letters to brothers who had died. Checked by
     * {@code OfferFilters} for every objective of every otherwise-eligible quest, and again at accept
     * time, because the world can change between the menu opening and the button being clicked.
     *
     * <p>Implementations must be cheap and side-effect free, and must read through the pass's shared
     * {@code McaVillagerSnapshot} rather than querying MCA per quest — this runs last in the filter chain
     * precisely because it is the most expensive question asked.
     *
     * <p>Defaults to empty (offerable), so existing objective types — including those registered by
     * add-ons — are unaffected and keep compiling.
     */
    default java.util.Optional<Component> unofferableReason(QuestContext context) {
        return java.util.Optional.empty();
    }

    /**
     * Why this objective cannot be evaluated <em>right now</em>, if it cannot — the reason shown to the
     * player in place of its progress line.
     *
     * <p>This is not failure and not completion: it is "the thing this objective reads about is not
     * here". The canonical case is an optional companion mod that was installed when the quest was
     * accepted and has since been removed. A quest in that state keeps its progress and its frozen
     * baselines, stops polling, never auto-fails, never reads as complete, stays abandonable, and picks
     * up exactly where it left off if the mod comes back — see {@code TownsteadObjective}.
     *
     * <p>Deliberately <b>derived rather than stored</b>: implementations answer from live state every
     * pass, so recovery needs no migration and no bookkeeping can go stale. The one thing that is
     * persisted is elapsed suspended time, because deadlines must not run down while a quest is
     * unplayable ({@code ActiveQuest#suspendedTicks}).
     *
     * <p>Defaults to empty, so existing objective types — including those registered by add-ons — are
     * unaffected and keep compiling.
     */
    default java.util.Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                            ObjectiveProgress progress, ServerLevel level) {
        return java.util.Optional.empty();
    }

    /**
     * Where this objective wants the player to go <em>right now</em>, if it can say.
     *
     * <p>The mod could always answer "who" ({@link VillagerTargeted}) and "where, roughly"
     * ({@code LocationAnchor}), but neither answer ever reached the client as something it could
     * draw. A player was told to fetch six nether wart and left to work out on their own that this
     * meant building a portal. This is the question whose absence made that possible.
     *
     * <p>Three rules implementations must keep, because a marker is a promise:
     * <ul>
     *   <li><b>Answer empty once satisfied.</b> The caller checks too, but an objective that keeps
     *       pointing after it is done is how a stale marker outlives its quest.</li>
     *   <li><b>Answer empty rather than guess.</b> There is no marker for "eight prismarine
     *       crystals", and inventing one sends the player somewhere confidently wrong — worse than
     *       sending them nowhere, because they will go. See {@code SourceHint}.</li>
     *   <li><b>Do not search the world from here.</b> This runs about once a second per player.
     *       Anything as expensive as {@code /locate} goes through {@code LocateCache}, which runs it
     *       once per objective and remembers the answer across restarts.</li>
     * </ul>
     *
     * <p>Defaults to empty, so existing objective types — including those registered by add-ons —
     * are unaffected and keep compiling, exactly like {@link #icon} and {@link #unofferableReason}
     * before it.
     */
    default java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        return java.util.Optional.empty();
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
