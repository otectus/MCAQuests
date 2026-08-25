package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Complete when a villager has advanced far enough in a Townstead profession (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_profession_progress",
 *   "target": "giver",
 *   "profession": "minecraft:farmer",
 *   "xp_delta": 120,
 *   "require_current_profession": true
 * }
 * }</pre>
 *
 * <p>Exactly one of {@code xp_delta}, {@code target_xp} or {@code target_tier} says what "far enough"
 * means: a relative gain from where they started, an absolute total, or a tier reached.
 *
 * <p><b>A profession change pauses, it does not reset.</b> With
 * {@code require_current_profession} on, a villager who is retrained mid-quest stops accruing rather
 * than losing what they had — their old XP is still recorded against the old profession, and if they
 * return to it the objective picks up exactly where it stopped. Wiping progress because a player
 * experimented with a workstation would be a nasty surprise for something that takes in-game days.
 */
public record TownsteadProfessionProgressObjective(TownsteadTarget target, String profession,
                                                   OptionalInt xpDelta, OptionalInt targetXp,
                                                   OptionalInt targetTier,
                                                   boolean requireCurrentProfession)
        implements PollingObjective, TownsteadObjective {

    private static final String K_PATH = "profession.xp";

    /**
     * Split from {@link #CODEC} so the record type can be inferred: chaining flatXmap straight
     * onto RecordCodecBuilder.create leaves it with no target type to infer from.
     */
    private static final Codec<TownsteadProfessionProgressObjective> BASE = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadProfessionProgressObjective::target),
                    Codec.STRING.fieldOf("profession")
                            .forGetter(TownsteadProfessionProgressObjective::profession),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "xp_delta")
                            .forGetter((TownsteadProfessionProgressObjective o) -> box(o.xpDelta())),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "target_xp")
                            .forGetter((TownsteadProfessionProgressObjective o) -> box(o.targetXp())),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "target_tier")
                            .forGetter((TownsteadProfessionProgressObjective o) -> box(o.targetTier())),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_current_profession", true)
                            .forGetter(TownsteadProfessionProgressObjective::requireCurrentProfession)
            ).apply(instance, (target, profession, delta, xp, tier, requireCurrent) ->
                    new TownsteadProfessionProgressObjective(target, profession, unbox(delta), unbox(xp),
                            unbox(tier), requireCurrent)));

    /** Validated at parse time, so a contradictory goal fails the reload rather than a quest. */
    public static final Codec<TownsteadProfessionProgressObjective> CODEC =
            BASE.flatXmap(TownsteadProfessionProgressObjective::validateGoal, DataResult::success);

    private static DataResult<TownsteadProfessionProgressObjective> validateGoal(
            TownsteadProfessionProgressObjective objective) {
        int goals = (objective.xpDelta.isPresent() ? 1 : 0)
                + (objective.targetXp.isPresent() ? 1 : 0)
                + (objective.targetTier.isPresent() ? 1 : 0);
        if (goals == 0) {
            return DataResult.error(() -> "townstead_profession_progress needs one of 'xp_delta', "
                    + "'target_xp' or 'target_tier'");
        }
        if (goals > 1) {
            return DataResult.error(() -> "townstead_profession_progress takes exactly one of 'xp_delta', "
                    + "'target_xp' or 'target_tier'; they mean different things and cannot be combined");
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
        return ObjectiveTypes.TOWNSTEAD_PROFESSION_PROGRESS;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_VILLAGER, TownsteadCapability.READ_PROFESSION);
    }

    @Override
    public int required() {
        if (xpDelta.isPresent()) {
            return xpDelta.getAsInt();
        }
        if (targetXp.isPresent()) {
            return targetXp.getAsInt();
        }
        return Math.max(1, targetTier.orElse(1));
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
        if (xpDelta.isEmpty()) {
            return; // absolute goals need no starting point
        }
        Entity villager = resolve(player, active, progress, level);
        TownsteadVillagerView view = read(villager);
        if (view == null) {
            return;
        }
        TownsteadBaseline.freeze(progress, "villager", K_PATH, villager.getUUID(),
                view.professionXp(), level.getGameTime());
        progress.setTargetUuid(villager.getUUID());
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        ServerLevel level = (ServerLevel) player.level();
        Entity villager = resolve(player, quest, progress, level);
        TownsteadVillagerView view = read(villager);
        if (view == null) {
            return false;
        }
        if (requireCurrentProfession && !matchesProfession(view)) {
            return false; // retrained: pause, keeping every point of what they already earned
        }
        if (xpDelta.isPresent() && !TownsteadBaseline.isFrozen(progress)) {
            freezeBaseline(player, quest, progress, level);
            return false;
        }

        int reached = reached(view, progress);
        if (reached <= progress.count()) {
            return false; // never walk progress backwards, whatever Townstead now reports
        }
        progress.setCount(Math.min(required(), reached));
        return true;
    }

    /** How far along the goal this reading puts them, in the units {@link #required()} counts in. */
    private int reached(TownsteadVillagerView view, ObjectiveProgress progress) {
        if (targetTier.isPresent()) {
            return view.professionLevel();
        }
        if (targetXp.isPresent()) {
            return view.professionXp();
        }
        OptionalDouble baseline = TownsteadBaseline.number(progress);
        return baseline.isEmpty() ? 0 : (int) Math.max(0, view.professionXp() - baseline.getAsDouble());
    }

    private boolean matchesProfession(TownsteadVillagerView view) {
        return view.professionId().toLowerCase(Locale.ROOT).equals(profession.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private Entity resolve(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           ServerLevel level) {
        return TownsteadTargetResolver.resolveForObjective(target, player, active, progress, level)
                .orElse(null);
    }

    @Nullable
    private static TownsteadVillagerView read(@Nullable Entity villager) {
        return villager == null ? null : new TownsteadEvaluation().villager(villager).orElse(null);
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        // An absolute goal a villager already meets really is free money, so withhold the offer. A
        // relative goal cannot be, because its baseline has not been taken yet.
        if (xpDelta.isPresent()) {
            return false;
        }
        TownsteadVillagerView view = read(context.villager());
        if (view == null || (requireCurrentProfession && !matchesProfession(view))) {
            return false;
        }
        return targetTier.isPresent()
                ? view.professionLevel() >= targetTier.getAsInt()
                : view.professionXp() >= targetXp.orElse(Integer.MAX_VALUE);
    }

    @Override
    public Component describe() {
        if (targetTier.isPresent()) {
            return Component.translatable("mcaquests.objective.townstead_profession_tier",
                    targetTier.getAsInt(), profession);
        }
        return Component.translatable("mcaquests.objective.townstead_profession_xp",
                required(), profession);
    }
}
