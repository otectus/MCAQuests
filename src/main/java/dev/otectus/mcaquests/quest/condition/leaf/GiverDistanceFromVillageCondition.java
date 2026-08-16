package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Offered only when the giver villager is at least {@code min_distance} blocks from the center of its MCA
 * home village (and, when {@code require_outside_border} is set, also outside the village border). This is
 * the gate for "out in the wilds" content such as lead-style escorts — pair it with
 * {@code mcaquests:time} {@code NIGHT} via {@code any_of} for "far from home, or after dark".
 *
 * <p>Fails safe to <b>not met</b> when the giver has no home village or its center cannot be resolved, so a
 * villager standing in its own square is never offered an "escort me home" quest.
 */
public record GiverDistanceFromVillageCondition(double minDistance, boolean requireOutsideBorder)
        implements QuestCondition {

    public static final Codec<GiverDistanceFromVillageCondition> CODEC =
            RecordCodecBuilder.<GiverDistanceFromVillageCondition>create(instance -> instance.group(
                    Codec.DOUBLE.lenientOptionalFieldOf("min_distance", 0.0D).forGetter(GiverDistanceFromVillageCondition::minDistance),
                    Codec.BOOL.lenientOptionalFieldOf("require_outside_border", false).forGetter(GiverDistanceFromVillageCondition::requireOutsideBorder)
            ).apply(instance, GiverDistanceFromVillageCondition::new))
            .flatXmap(GiverDistanceFromVillageCondition::validate, GiverDistanceFromVillageCondition::validate);

    private static DataResult<GiverDistanceFromVillageCondition> validate(GiverDistanceFromVillageCondition condition) {
        if (condition.minDistance < 0.0D) {
            return DataResult.error(() ->
                    "mcaquests:giver_distance_from_village 'min_distance' must be >= 0, was " + condition.minDistance);
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.GIVER_DISTANCE_FROM_VILLAGE;
    }

    @Override
    public boolean test(QuestContext context) {
        if (!context.mca().hasHomeVillage()) {
            return false;
        }
        Optional<BlockPos> center = context.mca().homeVillageCenter();
        if (center.isEmpty()) {
            return false;
        }
        BlockPos giverPos = context.villager().blockPosition();
        boolean farEnough = minDistance <= 0.0D
                || center.get().distSqr(giverPos) >= minDistance * minDistance;
        if (!farEnough) {
            return false;
        }
        if (!requireOutsideBorder) {
            return true;
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(context.villager());
        return villageId.isEmpty()
                || !McaCompat.isWithinVillage(context.level(), villageId.getAsInt(), giverPos);
    }
}
