package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Rest by sleeping through to morning. Credited on Forge's player wake/sleep-finished events when the
 * player slept long enough. (The "ensure a villager reaches their bed" variant is not implemented;
 * MCA does not expose villager sleep state reliably — see DATAPACK.md.)
 */
public record SleepOrRestObjective(boolean requireMorning) implements QuestObjective {

    public static final MapCodec<SleepOrRestObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("require_morning", true).forGetter(SleepOrRestObjective::requireMorning)
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
     * The player's own bed, when they have one.
     *
     * <p>Deliberately not the giver's bed, which is what a villager-centred reading would reach for:
     * this objective is the <em>player</em> sleeping, and sending them to somebody else's house to do
     * it would be a marker on a place that has nothing to do with the task. Their respawn point is the
     * bed the game itself already agrees is theirs.
     *
     * <p>A player who has not slept anywhere has no bed, and gets no marker — the instruction is "find
     * somewhere to sleep", and the mod has no idea where that will be.
     */
    @Override
    public Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                             ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        BlockPos bed = player.getRespawnPosition();
        if (bed == null) {
            return Optional.empty();
        }
        /* A bed is a block, so the marker only clears once the player is practically on it. */
        final int arriveRadius = 4;
        return Optional.of(GuidanceTarget.ofPos(bed, player.getRespawnDimension(), GuidanceKind.HOME,
                Component.translatable("mcaquests.guidance.your_bed"), arriveRadius, false));
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
