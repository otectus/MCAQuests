package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Complete when a village's spirit has grown far enough (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_spirit_progress",
 *   "spirit": "industrious",
 *   "points_delta": 60,
 *   "target_tier": 2
 * }
 * }</pre>
 *
 * <p>Spirit accrues from what a village builds, so this is the long-horizon "make this place into
 * something" objective. {@code points_delta} measures from a baseline frozen at accept; {@code
 * target_tier} is absolute. When {@code spirit} names one, points are that spirit's; otherwise they
 * are the village total.
 */
public record TownsteadSpiritProgressObjective(Optional<String> spirit, OptionalInt pointsDelta,
                                               OptionalInt targetTier)
        implements PollingObjective, TownsteadObjective {

    private static final String K_PATH = "spirit.points";

    /**
     * Split from {@link #CODEC} so the record type can be inferred: chaining flatXmap straight
     * onto RecordCodecBuilder.create leaves it with no target type to infer from.
     */
    private static final Codec<TownsteadSpiritProgressObjective> BASE = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritProgressObjective::spirit),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "points_delta")
                            .forGetter((TownsteadSpiritProgressObjective o) -> box(o.pointsDelta())),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "target_tier")
                            .forGetter((TownsteadSpiritProgressObjective o) -> box(o.targetTier()))
            ).apply(instance, (spirit, delta, tier) ->
                    new TownsteadSpiritProgressObjective(spirit, unbox(delta), unbox(tier))));

    /** Validated at parse time, so a contradictory goal fails the reload rather than a quest. */
    public static final Codec<TownsteadSpiritProgressObjective> CODEC =
            BASE.flatXmap(TownsteadSpiritProgressObjective::validateGoal, DataResult::success);

    private static DataResult<TownsteadSpiritProgressObjective> validateGoal(
            TownsteadSpiritProgressObjective objective) {
        if (objective.pointsDelta.isEmpty() && objective.targetTier.isEmpty()) {
            return DataResult.error(() ->
                    "townstead_spirit_progress needs 'points_delta' or 'target_tier'");
        }
        if (objective.pointsDelta.isPresent() && objective.targetTier.isPresent()) {
            return DataResult.error(() -> "townstead_spirit_progress takes one of 'points_delta' or "
                    + "'target_tier', not both: one is relative and the other absolute");
        }
        return DataResult.success(objective);
    }

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_SPIRIT_PROGRESS;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_SPIRIT);
    }

    @Override
    public int required() {
        return pointsDelta.orElseGet(() -> targetTier.orElse(1));
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(required(), progress.count());
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= required();
    }

    @Override
    public void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                               ServerLevel level) {
        if (pointsDelta.isEmpty()) {
            return;
        }
        TownsteadSpiritView view = read(active, level);
        if (view != null) {
            TownsteadBaseline.freeze(progress, "spirit", K_PATH, null, points(view), level.getGameTime());
        }
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        ServerLevel level = (ServerLevel) player.level();
        TownsteadSpiritView view = read(quest, level);
        if (view == null) {
            return false;
        }
        if (pointsDelta.isPresent() && !TownsteadBaseline.isFrozen(progress)) {
            freezeBaseline(player, quest, progress, level);
            return false;
        }

        int reached;
        if (targetTier.isPresent()) {
            reached = view.tier();
        } else {
            OptionalDouble baseline = TownsteadBaseline.number(progress);
            reached = baseline.isEmpty() ? 0 : (int) Math.max(0, points(view) - baseline.getAsDouble());
        }
        if (reached <= progress.count()) {
            return false; // spirit can fall when a building is lost; never walk the player backwards
        }
        progress.setCount(Math.min(required(), reached));
        return true;
    }

    private int points(TownsteadSpiritView view) {
        return spirit.map(view::pointsFor).orElseGet(view::total);
    }

    @Nullable
    private TownsteadSpiritView read(ActiveQuest active, ServerLevel level) {
        Entity giver = level.getEntity(active.villagerUuid());
        if (giver == null) {
            return null;
        }
        OptionalInt village = McaCompat.getHomeVillageId(giver);
        return village.isEmpty()
                ? null
                : new TownsteadEvaluation().spirit(level, village.getAsInt()).orElse(null);
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        if (targetTier.isEmpty()) {
            return false;
        }
        OptionalInt village = McaCompat.getHomeVillageId(context.villager());
        return village.isPresent() && context.mca().townstead()
                .spirit(context.level(), village.getAsInt())
                .map(view -> view.tier() >= targetTier.getAsInt())
                .orElse(false);
    }

    @Override
    public Component describe() {
        return Component.translatable(targetTier.isPresent()
                        ? "mcaquests.objective.townstead_spirit_tier"
                        : "mcaquests.objective.townstead_spirit_points",
                required(), spirit.orElse(""));
    }
}
