package dev.otectus.mcaquests.quest.condition.leaf;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.village.VillageProximity;

/**
 * Offered only when the giver villager is at least {@code min_distance} blocks — measured horizontally —
 * from <b>any</b> village: a vanilla or modded village structure in the {@code #mcaquests:villages} tag,
 * an MCA Reborn village, or an MCA Capitals capital. This is the "genuinely out in the wilds" gate, as
 * opposed to {@code mcaquests:giver_distance_from_village}, which only measures the giver's own MCA home
 * village and so still passes for a villager standing in somebody else's town.
 *
 * <p>Finding structure villages happens off the server thread, so the answer can be "not yet known" for
 * a few ticks. This condition is then <b>not met</b>: a quest gated on being far from anywhere is
 * withheld until that is proven, never offered on the assumption.
 */
public record GiverDistanceFromAnyVillageCondition(double minDistance) implements QuestCondition {

    // Validate on the MapCodec, never on a Codec chained off create(...): the dispatch registry
    // takes a MapCodec so the fields stay inline beside "type" rather than under a nested
    // "value" key, which optionalFieldOf would then swallow silently.
    // See DispatchedCodecInlinesTest.
    public static final MapCodec<GiverDistanceFromAnyVillageCondition> CODEC =
            RecordCodecBuilder.<GiverDistanceFromAnyVillageCondition>mapCodec(instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.DOUBLE, "min_distance", 0.0D).forGetter(GiverDistanceFromAnyVillageCondition::minDistance)
            ).apply(instance, GiverDistanceFromAnyVillageCondition::new))
            .flatXmap(GiverDistanceFromAnyVillageCondition::validate, GiverDistanceFromAnyVillageCondition::validate);

    private static DataResult<GiverDistanceFromAnyVillageCondition> validate(GiverDistanceFromAnyVillageCondition condition) {
        if (condition.minDistance < 0.0D) {
            return DataResult.error(() ->
                    "mcaquests:giver_distance_from_any_village 'min_distance' must be >= 0, was " + condition.minDistance);
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.GIVER_DISTANCE_FROM_ANY_VILLAGE;
    }

    @Override
    public boolean test(QuestContext context) {
        return VillageProximity.check(context.level(), context.villager().blockPosition(), minDistance)
                == VillageProximity.Nearness.FAR;
    }
}
