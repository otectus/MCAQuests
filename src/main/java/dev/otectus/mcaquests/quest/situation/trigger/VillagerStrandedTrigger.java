package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;
import net.minecraft.util.ExtraCodecs;

/**
 * Opens when a resident is a long way from home after dark and has stayed there (spec §5.8).
 *
 * <pre>{@code
 * { "type": "mcaquests:villager_stranded", "minimum_distance": 96, "hold_ticks": 600 }
 * }</pre>
 *
 * <p><b>Contains no Townstead.</b> Home villages, borders and residency are MCA concepts, so this works
 * on a plain MCA install and the situations built on it play identically whether Townstead is present,
 * absent, or removed mid-world.
 *
 * <p>The hold is what makes it a rescue rather than a nuisance: a villager who stepped past the border
 * for a moment is not stranded, and firing on that would mean a stream of alerts every evening.
 * {@code require_night} is on by default for the same reason — being outside the walls is only
 * frightening after dark.
 */
public record VillagerStrandedTrigger(int minimumDistance, int holdTicks,
                                      boolean requireNight) implements SituationTrigger {

    public static final Codec<VillagerStrandedTrigger> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_distance", 96)
                            .forGetter(VillagerStrandedTrigger::minimumDistance),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "hold_ticks", 600)
                            .forGetter(VillagerStrandedTrigger::holdTicks),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_night", true)
                            .forGetter(VillagerStrandedTrigger::requireNight)
            ).apply(instance, VillagerStrandedTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.VILLAGER_STRANDED;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.VILLAGER_STRANDED;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        // magnitude carries the observed distance from the border, in blocks. fullMoon is reused as
        // "it is night", which is already what the night trigger means by that field.
        return signal.magnitude() >= minimumDistance && (!requireNight || signal.fullMoon());
    }
}
