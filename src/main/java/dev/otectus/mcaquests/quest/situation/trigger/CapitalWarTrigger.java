package dev.otectus.mcaquests.quest.situation.trigger;

import com.mojang.serialization.Codec;
import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.SituationTrigger;
import dev.otectus.mcaquests.quest.situation.SituationTriggerType;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;

/**
 * Opens when a capital declares war on another capital (1.6.0).
 *
 * <pre>{@code
 * { "type": "mcaquests:capital_war" }
 * }</pre>
 *
 * <p>No parameters: a war is news for both capitals whatever the relation was beforehand, and the
 * detector raises it once per village on each side. The relation it came out of rides along on the
 * signal's context for a definition that wants to read it, but nothing here gates on it -- an alliance
 * collapsing and a truce lapsing are both wars.
 *
 * <p>The detector only fires on the crossing into war, so a war that lasts a fortnight opens this once.
 */
public record CapitalWarTrigger() implements SituationTrigger {

    public static final Codec<CapitalWarTrigger> CODEC = Codec.unit(CapitalWarTrigger::new);

    @Override
    public SituationTriggerType<?> type() {
        return SituationTriggerTypes.CAPITAL_WAR;
    }

    @Override
    public SituationSignalType signalType() {
        return SituationSignalType.CAPITAL_WAR;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return true;
    }
}
