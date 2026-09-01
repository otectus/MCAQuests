package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Opens when a relative of a villager goes missing (unloaded/not in the village) — see
 * {@code McaCompat.relativesWithStatus(..., "missing")} (0.8.0). {@code relation} narrows which kin
 * ({@code any}/{@code spouse}/{@code parent}/{@code child}/{@code sibling}).
 */
public record MissingKinTrigger(String relation) implements SituationTrigger {

    public static final Codec<MissingKinTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("relation", "any").forGetter(MissingKinTrigger::relation)
    ).apply(instance, MissingKinTrigger::new));

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.MISSING_KIN;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.MISSING_KIN;
    }

    /**
     * Narrows to the relation this trigger names.
     *
     * <p>Until 1.4.3 this returned {@code true} unconditionally and the {@code relation} field was parsed
     * and ignored — which is why {@code find_missing_child}, whose trigger says {@code "relation":
     * "child"}, opened just as readily when a villager's spouse went missing.
     *
     * <p>Fails closed when the signal cannot answer: a narrowed trigger that cannot confirm its relation
     * does not fire, rather than firing as if it had no filter at all. An unnarrowed {@code any} needs
     * nothing confirmed and behaves exactly as it always did.
     */
    @Override
    public boolean matches(TriggerSignal signal) {
        if (relation.equals("any")) {
            return true;
        }
        ServerLevel level = signal.level();
        UUID villagerUuid = signal.villagerUuid();
        if (level == null || villagerUuid == null) {
            return false;
        }
        Entity villager = level.getEntity(villagerUuid);
        return villager != null && McaCompat.relativesWithStatus(level, villager, relation, "missing");
    }
}
