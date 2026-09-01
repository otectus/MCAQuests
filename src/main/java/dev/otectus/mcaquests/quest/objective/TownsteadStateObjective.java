package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadQuery;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.TownsteadNames;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * Complete when a Townstead value stays true for long enough (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_state",
 *   "target": "giver",
 *   "source": "villager",
 *   "path": "schedule.currentActivity",
 *   "operator": "eq",
 *   "value": "work",
 *   "hold_ticks": 600,
 *   "reset_on_false": true
 * }
 * }</pre>
 *
 * <p>This is the "and keep it that way" objective. {@code reset_on_false} is what separates
 * <em>sustained</em> from <em>cumulative</em>: with it on, a villager who stops working sends the timer
 * back to zero and the player has to give them an uninterrupted stretch; with it off, the time simply
 * stops accruing and picks up again later.
 *
 * <p>Progress is counted in seconds rather than ticks purely so the log reads as {@code (17/30)}
 * instead of {@code (340/600)}.
 */
public record TownsteadStateObjective(TownsteadQuery query, int holdTicks,
                                      boolean resetOnFalse) implements PollingObjective, TownsteadObjective {

    private static final int TICKS_PER_SECOND = 20;

    public static final Codec<TownsteadStateObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    TownsteadQuery.MAP_CODEC.forGetter(TownsteadStateObjective::query),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", TICKS_PER_SECOND)
                            .forGetter(TownsteadStateObjective::holdTicks),
                    StrictCodecs.strictOptional(Codec.BOOL, "reset_on_false", true)
                            .forGetter(TownsteadStateObjective::resetOnFalse)
            ).apply(instance, TownsteadStateObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_STATE;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return TownsteadObjectives.capabilitiesFor(query);
    }

    @Override
    public int required() {
        return Math.max(1, holdTicks / TICKS_PER_SECOND);
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(required(), (int) (progress.elapsedTicks() / TICKS_PER_SECOND));
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.elapsedTicks() >= holdTicks;
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        if (isSatisfied(player, progress)) {
            return false; // latched: a satisfied hold is never un-satisfied by a later reading
        }
        ServerLevel level = (ServerLevel) player.level();
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        Entity target = TownsteadObjectives.subjectEntity(query, player, quest, progress, level);
        if (target == null && !query.source().isGlobal()) {
            return handleFalse(progress);
        }
        boolean holds = query.testResolved(evaluation.subject(query, target),
                TownsteadEvaluation.effectivePath(query));
        if (!holds) {
            return handleFalse(progress);
        }
        progress.addElapsed(TICKS_PER_SECOND);
        return true;
    }

    private boolean handleFalse(ObjectiveProgress progress) {
        if (resetOnFalse && progress.elapsedTicks() > 0L) {
            progress.resetElapsed();
            return true; // the player's visible progress really did change, so the log must refresh
        }
        return false;
    }

    /**
     * Never withheld as free money: a hold objective takes real time even when the state is already
     * true at the moment of the offer, so there is nothing to trivially satisfy.
     */
    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        return false;
    }

    /**
     * "Keep them working for 30 seconds", not "Keep villager.schedule.currentActivity eq work for 30".
     *
     * <p>A one-second hold gets its own wording. It is not really a hold at all -- it is "finish in this
     * state" -- and "for 1 seconds" is both wrong and the sort of thing nobody fixes because it is only
     * ever slightly wrong.
     */
    @Override
    public Component describe() {
        return required() <= 1
                ? Component.translatable("mcaquests.objective.townstead_state.now",
                        TownsteadNames.clause(query))
                : Component.translatable("mcaquests.objective.townstead_state",
                        TownsteadNames.clause(query), required());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return describe();
    }
}
