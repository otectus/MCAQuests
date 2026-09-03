package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;
import net.minecraft.util.ExtraCodecs;

/**
 * Opens when hostile mobs gather at a resident's door (spec §5.8).
 *
 * <pre>{@code
 * { "type": "mcaquests:hostiles_near_home", "count": 3, "radius": 16, "hold_ticks": 200 }
 * }</pre>
 *
 * <p><b>Contains no Townstead</b>, and deliberately searches a small box around a known bed or village
 * centre rather than the world. A detector that swept a dimension for hostiles would be the most
 * expensive thing this mod does, on every server, forever. Bounding it to the places that already
 * matter costs almost nothing and finds exactly the mobs a player would care about.
 *
 * <p>{@code hold_ticks} keeps a zombie passing through from becoming an emergency; the mobs have to
 * still be there when the detector looks again.
 */
public record HostilesNearHomeTrigger(int count, int radius, int holdTicks) implements SituationTrigger {

    public static final MapCodec<HostilesNearHomeTrigger> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 3)
                            .forGetter(HostilesNearHomeTrigger::count),
                    StrictCodecs.strictOptional(Codec.intRange(1, 64), "radius", 16)
                            .forGetter(HostilesNearHomeTrigger::radius),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", 200)
                            .forGetter(HostilesNearHomeTrigger::holdTicks)
            ).apply(instance, HostilesNearHomeTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.HOSTILES_NEAR_HOME;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.HOSTILES_NEAR_HOME;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return signal.magnitude() >= count;
    }
}
