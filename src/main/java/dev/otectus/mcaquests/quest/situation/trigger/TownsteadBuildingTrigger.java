package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

import java.util.Optional;

/**
 * Opens when a village registers or upgrades a building (Townstead spec 7.3).
 *
 * <pre>{@code { "type": "mcaquests:townstead_building", "building_type": "dock", "minimum_level": 1 } }</pre>
 *
 * <p>{@code building_type} matches a family, so {@code dock} covers every dock tier and the level is
 * what narrows it.
 */
public record TownsteadBuildingTrigger(Optional<String> buildingType, int minimumLevel)
        implements SituationTrigger {

    public static final Codec<TownsteadBuildingTrigger> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "building_type")
                            .forGetter(TownsteadBuildingTrigger::buildingType),
                    StrictCodecs.strictOptional(Codec.INT, "minimum_level", 1)
                            .forGetter(TownsteadBuildingTrigger::minimumLevel)
            ).apply(instance, TownsteadBuildingTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.TOWNSTEAD_BUILDING;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.TOWNSTEAD_BUILDING;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        if (signal.magnitude() < minimumLevel) {
            return false;
        }
        return buildingType.isEmpty()
                || signal.signalContext().map(c -> c.matchesString(buildingType.get())).orElse(false);
    }
}
