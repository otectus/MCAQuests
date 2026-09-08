package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;

import java.util.Optional;

/**
 * Rest by sleeping. With {@code require_morning} (the default) the night has to actually pass, so the
 * credit comes from the sleep-finished event; without it, getting out of bed after sleeping long
 * enough is the rest, so the credit comes from the wake event instead. (The "ensure a villager reaches their bed" variant is not implemented;
 * MCA does not expose villager sleep state reliably — see DATAPACK.md.)
 */
public record SleepOrRestObjective(boolean requireMorning) implements QuestObjective {

    public static final Codec<SleepOrRestObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.strictOptional(Codec.BOOL, "require_morning", true).forGetter(SleepOrRestObjective::requireMorning)
    ).apply(instance, SleepOrRestObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.SLEEP_OR_REST;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.sleep_or_rest");
    }


    /**
     * The player's own bed, when the game can still show them one, and a plain instruction when it
     * cannot.
     *
     * <p>Deliberately not the giver's bed, which is what a villager-centred reading would reach for:
     * this objective is the <em>player</em> sleeping, and sending them to somebody else's house to do
     * it would be a marker on a place that has nothing to do with the task. Their respawn point is the
     * bed the game itself already agrees is theirs.
     *
     * <p>But a respawn point is not proof of a bed. It survives the bed being mined, it is set by a
     * respawn anchor, it is forced by {@code /spawnpoint} onto bare ground, and it can name a
     * dimension the player is nowhere near. A marker labelled "your bed" on any of those is a lie the
     * player walks a long way to discover, so the bed is only called a bed when there is one there to
     * look at — see {@link #isKnownBed}. Otherwise the answer is the instruction "sleep in a bed",
     * which is true wherever they are standing.
     */
    @Override
    public Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                             ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        BlockPos bed = player.getRespawnPosition();
        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (bed != null && dimension.equals(level.dimension()) && !player.isRespawnForced()
                && isKnownBed(level, bed)) {
            /* A bed is a block, so the marker only clears once the player is practically on it. */
            final int arriveRadius = 4;
            return Optional.of(GuidanceTarget.ofPos(bed, level, GuidanceKind.HOME,
                    Component.translatable("mcaquests.guidance.your_bed"), arriveRadius, false));
        }
        return Optional.of(GuidanceTarget.instruction(level.dimension(),
                Component.translatable("mcaquests.guidance.bed.unknown")));
    }

    /**
     * Whether {@code pos} in {@code level} is a bed somebody could go and lie in right now.
     *
     * <p>An unloaded chunk answers no rather than loading it: guidance is recomputed about once a
     * second for every player with an active quest, and forcing a chunk load on that cadence to check
     * a block is a far worse trade than telling the player to find a bed.
     */
    public static boolean isKnownBed(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    /**
     * Whether morning arriving credits this objective. {@code require_morning} is the whole question:
     * the objective that asked to be woken by the sun is the objective the sunrise finishes.
     */
    public static boolean creditsOnMorning(boolean requireMorning) {
        return requireMorning;
    }

    /**
     * Whether getting out of bed credits this objective.
     *
     * <p>Only for {@code require_morning = false}, which is the setting that means "a rest, not
     * necessarily a night" — on a server where somebody else is awake, morning may never come and the
     * sleep-finished event never fires. Two things still have to be true: the player must have been
     * asleep long enough for vanilla to count it as rest, and the wake must not be a bounce-out, since
     * a bed made unusable underneath a player or a disconnect is not a night's sleep.
     */
    public static boolean creditsOnWake(boolean requireMorning, boolean wokeImmediately,
                                        boolean sleptLongEnough) {
        return !requireMorning && !wokeImmediately && sleptLongEnough;
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
        return true;
    }
}
